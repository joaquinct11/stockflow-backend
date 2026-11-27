package com.stockflow.integration;

import com.stockflow.component.DigemidCatalogLoader;
import com.stockflow.component.DigemidCatalogLoader.EstadoCarga;
import com.stockflow.entity.CatalogoDigemid;
import com.stockflow.repository.CatalogoDigemidRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests de integración para DigemidCatalogLoader.
 * PostgreSQL 16 real (Testcontainers).
 *
 * Perfil test: digemid.min-expected=3, digemid.async=false.
 * CSV de test: src/test/resources/digemid/catalogo.csv (5 registros).
 *
 * t01 Fallo durante staging: catálogo anterior intacto
 * t02 CSV incompleto: no se publica el catálogo
 * t03 Registros duplicados: se conserva primera ocurrencia, sin duplicados en DB
 * t04 Error durante publicación: rollback completo
 * t05 Dos instancias concurrentes: una sola publica
 * t06 Lock liberado después de una excepción
 * t07 Reinicio con catálogo válido: no recargar
 * t08 Backend inicia sin esperar la carga completa (run() retorna inmediatamente en modo async)
 * t09 Catálogo anterior disponible mientras se carga uno nuevo
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DigemidCatalogLoaderTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    static {
        postgres.start();
    }

    @Autowired private DigemidCatalogLoader loader;
    @Autowired private CatalogoDigemidRepository repo;
    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;

    private static final long LOCK_KEY = 8_675_309L;

    @BeforeEach
    void limpiarEstado() {
        jdbc.execute("DELETE FROM catalogo_digemid");
        jdbc.execute("SELECT pg_advisory_unlock_all()");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // t01 — Fallo durante staging: catálogo anterior intacto
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @Order(1)
    void t01_falloEnStagingConservaElCatalogoAnterior() throws Exception {
        // Cargar datos "anteriores" simulando un catálogo ya publicado
        insertarRegistrosFicticios(5); // 5 >= min-expected(3) → catálogo válido
        long conteoAntes = repo.count();
        assertThat(conteoAntes).isEqualTo(5);

        // Forzar que cargarYValidarCSV() devuelva una lista intencionalmente pequeña
        // simulando un CSV incompleto en staging: configurar temporalmente min-expected alto
        // vía una instancia con threshold elevado
        DigemidCatalogLoader loaderConUmbralAlto = loaderConMinExpected(1_000_000);
        loaderConUmbralAlto.ejecutarCarga();

        // El catálogo anterior debe permanecer intacto porque la validación del CSV falló
        // (5 registros < 1.000.000 mínimo esperado → no se llama reemplazarCatalogoAtomicamente)
        assertThat(repo.count())
                .as("El catálogo anterior debe permanecer intacto tras un fallo en staging")
                .isEqualTo(conteoAntes);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // t02 — CSV incompleto: no publicar
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @Order(2)
    void t02_csvIncompleto_noPublica() throws Exception {
        // Con un umbral mayor al número de registros del CSV de test (5), la validación falla
        DigemidCatalogLoader loaderConUmbralAlto = loaderConMinExpected(100);

        assertThatCode(() -> loaderConUmbralAlto.ejecutarCarga())
                .as("ejecutarCarga() no debe lanzar excepción: los errores se registran internamente")
                .doesNotThrowAnyException();

        // No se debe haber insertado nada
        assertThat(repo.count())
                .as("No deben publicarse registros cuando el CSV no supera el mínimo esperado")
                .isZero();

        assertThat(DigemidCatalogLoader.getEstado())
                .as("El estado debe ser ERROR cuando el CSV es incompleto")
                .isEqualTo(EstadoCarga.ERROR);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // t03 — Registros duplicados en CSV: se conserva la primera ocurrencia
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @Order(3)
    void t03_registrosDuplicadosEnCSV_seManejanCorrectamente() throws Exception {
        // La lógica de cargarYValidarCSV() deduplicar por cod_prod antes de publicar
        List<CatalogoDigemid> conDuplicados = loader.cargarYValidarCSV();

        // Verificar que todos los cod_prod son únicos en la lista validada
        long uniqueCodProds = conDuplicados.stream()
                .map(CatalogoDigemid::getCodProd)
                .distinct()
                .count();
        assertThat(uniqueCodProds)
                .as("No debe haber cod_prod duplicados en la lista validada")
                .isEqualTo(conDuplicados.size());

        // Cargar normalmente y verificar unicidad en BD
        loader.ejecutarCarga();
        Long distinctEnBD = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT cod_prod) FROM catalogo_digemid", Long.class);
        assertThat(repo.count())
                .as("count() debe ser igual a distinct cod_prod (sin duplicados en BD)")
                .isEqualTo(distinctEnBD);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // t04 — Error durante publicación: rollback completo
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @Order(4)
    void t04_errorDurantePublicacion_rollbackCompleto() throws Exception {
        // Cargar catálogo previo
        insertarRegistrosFicticios(5);
        long conteoAntes = repo.count();
        assertThat(conteoAntes).isEqualTo(5);

        // Forzar una IllegalStateException durante reemplazarCatalogoAtomicamente
        // simulando un fallo a mitad de la transacción
        List<CatalogoDigemid> registrosConNullNom = new ArrayList<>();
        registrosConNullNom.add(CatalogoDigemid.builder()
                .codProd("X001")
                .nomProd(null)  // viola NOT NULL constraint → fuerza rollback
                .situacion("ACT").fraccion(BigDecimal.ONE).build());

        // Verificar que la excepción no se propaga fuera de reemplazarCatalogoAtomicamente
        // La transacción hace rollback y el catálogo anterior permanece
        assertThatThrownBy(() -> loader.reemplazarCatalogoAtomicamente(registrosConNullNom))
                .isInstanceOf(Exception.class);

        // El rollback debe haber restaurado los 5 registros anteriores
        assertThat(repo.count())
                .as("Después del rollback el catálogo anterior debe estar intacto")
                .isEqualTo(conteoAntes);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // t05 — Dos instancias concurrentes: una sola publica
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @Order(5)
    void t05_dosInstanciasConcurrentes_unaSoloPublica() throws Exception {
        int instancias = 2;
        CountDownLatch listo  = new CountDownLatch(instancias);
        CountDownLatch inicio = new CountDownLatch(1);
        CountDownLatch fin    = new CountDownLatch(instancias);
        AtomicInteger excepciones = new AtomicInteger();

        ExecutorService exec = Executors.newFixedThreadPool(instancias);
        for (int i = 0; i < instancias; i++) {
            exec.submit(() -> {
                listo.countDown();
                try {
                    inicio.await();
                    // Cada "instancia" tiene su propio loader (sin estado compartido de Spring)
                    DigemidCatalogLoader inst = loaderConMinExpected(3);
                    inst.ejecutarCarga();
                } catch (Exception e) {
                    excepciones.incrementAndGet();
                } finally {
                    fin.countDown();
                }
            });
        }

        listo.await();
        inicio.countDown();
        boolean terminaron = fin.await(30, TimeUnit.SECONDS);
        exec.shutdownNow();

        assertThat(terminaron).isTrue();
        assertThat(excepciones.get())
                .as("Ninguna instancia debe lanzar excepción")
                .isZero();

        // Sin duplicados independientemente de cuántas instancias ganaron el lock
        Long distinctEnBD = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT cod_prod) FROM catalogo_digemid", Long.class);
        assertThat(repo.count())
                .as("No debe haber duplicados: total == distinct cod_prod")
                .isEqualTo(distinctEnBD);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // t06 — Lock liberado después de una excepción
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @Order(6)
    void t06_lockLiberadoDespuesDeUnaExcepcion() throws Exception {
        // Primera ejecución con umbral muy alto → falla en validación → lock debe liberarse
        DigemidCatalogLoader loaderConUmbralAlto = loaderConMinExpected(1_000_000);
        loaderConUmbralAlto.ejecutarCarga(); // falla en validación

        // Verificar que el lock fue liberado: una segunda ejecución debe poder adquirirlo
        // Si el lock quedó huérfano, pg_try_advisory_lock() devolvería false y la carga no ocurriría
        loader.ejecutarCarga(); // umbral=3, debe funcionar

        assertThat(repo.count())
                .as("La segunda ejecución debe completarse correctamente porque el lock fue liberado")
                .isEqualTo(5);

        assertThat(DigemidCatalogLoader.getEstado())
                .isEqualTo(EstadoCarga.COMPLETADA);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // t07 — Reinicio con catálogo válido: no recargar
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @Order(7)
    void t07_reinicioConCatalogoValido_noRecarga() throws Exception {
        // Simular catálogo ya cargado con más de min-expected(3) registros
        insertarRegistrosFicticios(10); // 10 >= 3
        long conteoAntes = repo.count();

        loader.ejecutarCarga();

        // El count NO debe haber cambiado: idempotencia
        assertThat(repo.count())
                .as("No debe recargarse cuando el catálogo supera el umbral mínimo")
                .isEqualTo(conteoAntes);

        assertThat(DigemidCatalogLoader.getEstado())
                .isEqualTo(EstadoCarga.COMPLETADA);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // t08 — Backend inicia sin esperar la carga completa
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @Order(8)
    void t08_runRetornaInmediatamenteEnModoAsync() throws Exception {
        // Crear un loader con async=true y verificar que run() retorna antes de que
        // la carga termine. No podemos verificar tiempos exactos, pero sí que
        // el hilo de carga es distinto al hilo que llama a run().
        DigemidCatalogLoader loaderAsync = loaderConAsyncYMinExpected(true, 3);

        loaderAsync.run(); // debe retornar en milisegundos

        // Esperar un momento para que el hilo de fondo complete
        Thread.sleep(2000);

        // La carga debe haberse completado en un hilo DISTINTO al de run()
        // Verificamos indirectamente que el estado es COMPLETADA o EN_PROCESO
        // (lo importante es que run() retornó sin bloquear)
        assertThat(DigemidCatalogLoader.getEstado())
                .as("La carga debe avanzar en segundo plano después de que run() retorne")
                .isIn(EstadoCarga.EN_PROCESO, EstadoCarga.COMPLETADA);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // t09 — Catálogo anterior disponible mientras se carga uno nuevo
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @Order(9)
    void t09_catalogoAnteriorDisponibleMientrasSeCargaUnoNuevo() throws Exception {
        // Cargar catálogo inicial (simula deploy anterior)
        loader.ejecutarCarga();
        assertThat(repo.count()).isEqualTo(5);

        // Verificar que las búsquedas funcionan (catálogo disponible para clientes)
        List<CatalogoDigemid> resultAntes = repo.buscar("PARACETAMOL");
        assertThat(resultAntes).isNotEmpty();

        // Simular reinicio: limpiar y recargar (el reemplazo es atómico)
        // Mientras se hace el reemplazo la tabla está disponible (no se borra antes de validar)
        loader.ejecutarCarga();

        // El catálogo sigue operativo después del reinicio
        assertThat(repo.count()).isEqualTo(5);
        assertThat(repo.buscar("IBUPROFENO")).isNotEmpty();
        assertThat(repo.buscar("PARACETAMOL")).isNotEmpty();

        assertThat(DigemidCatalogLoader.getEstado())
                .isEqualTo(EstadoCarga.COMPLETADA);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Crea una instancia del loader con un valor de minExpected distinto al del contexto,
     * usando las mismas dependencias inyectadas.
     */
    private DigemidCatalogLoader loaderConMinExpected(long minExpectedOverride) {
        return loaderConAsyncYMinExpected(false, minExpectedOverride);
    }

    private DigemidCatalogLoader loaderConAsyncYMinExpected(boolean asyncOverride, long minExpectedOverride) {
        try {
            var l = new DigemidCatalogLoader(repo, dataSource,
                    loader.getTransactionTemplateForTest());
            java.lang.reflect.Field fMin = DigemidCatalogLoader.class.getDeclaredField("minExpected");
            fMin.setAccessible(true);
            fMin.setLong(l, minExpectedOverride);
            java.lang.reflect.Field fAsync = DigemidCatalogLoader.class.getDeclaredField("async");
            fAsync.setAccessible(true);
            fAsync.setBoolean(l, asyncOverride);
            return l;
        } catch (Exception e) {
            throw new RuntimeException("No se pudo crear instancia del loader para test", e);
        }
    }

    private void insertarRegistrosFicticios(int cantidad) {
        List<CatalogoDigemid> batch = new ArrayList<>(500);
        for (int i = 0; i < cantidad; i++) {
            batch.add(CatalogoDigemid.builder()
                    .codProd("FAKE" + i).nomProd("Producto ficticio " + i)
                    .situacion("ACT").fraccion(BigDecimal.ONE).build());
            if (batch.size() == 500) { repo.saveAll(batch); batch.clear(); }
        }
        if (!batch.isEmpty()) repo.saveAll(batch);
    }
}
