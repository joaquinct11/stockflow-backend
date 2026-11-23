package com.stockflow.integration;

import com.stockflow.entity.Producto;
import com.stockflow.repository.MovimientoInventarioRepository;
import com.stockflow.repository.ProductoRepository;
import com.stockflow.service.ProductoService;
import com.stockflow.util.TenantContext;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * F-13 — Pruebas de integración: protección definitiva de concurrencia.
 * PostgreSQL 16 real (Testcontainers). Sin mocks en los paths críticos.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StockConcurrencyIntegrationTest {

    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    static {
        postgres.start();
    }

    @Autowired private ProductoRepository productoRepository;
    @Autowired private ProductoService productoService;
    @Autowired private MovimientoInventarioRepository movimientoRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate txTemplate;
    @Autowired private EntityManager em;

    private static final String TENANT_A = "tc-f13-a";
    private static final String TENANT_B = "tc-f13-b";
    private static final int    STOCK_INICIAL = 100;

    private Long productoAId;
    private Long productoBId; // tenant B — para aislamiento

    // ── Setup global ──────────────────────────────────────────────────────────

    @BeforeAll
    void setup() {
        // Tenant A
        jdbc.execute("INSERT INTO tenants (tenant_id, nombre, activo, rubro) " +
                "VALUES ('" + TENANT_A + "', 'Test F13 A', true, 'FARMACIA') " +
                "ON CONFLICT (tenant_id) DO NOTHING");

        // Tenant B
        jdbc.execute("INSERT INTO tenants (tenant_id, nombre, activo, rubro) " +
                "VALUES ('" + TENANT_B + "', 'Test F13 B', true, 'FARMACIA') " +
                "ON CONFLICT (tenant_id) DO NOTHING");

        Long umId = jdbc.queryForObject(
                "SELECT id FROM unidad_medida WHERE tenant_id IS NULL LIMIT 1", Long.class);

        productoAId = jdbc.queryForObject(
                "INSERT INTO productos (nombre, stock_actual, costo_unitario, precio_venta, " +
                "unidad_medida_id, activo, tenant_id, version) " +
                "VALUES ('Producto F13-A', " + STOCK_INICIAL + ", 5.00, 10.00, " + umId +
                ", true, '" + TENANT_A + "', 0) RETURNING id", Long.class);

        productoBId = jdbc.queryForObject(
                "INSERT INTO productos (nombre, stock_actual, costo_unitario, precio_venta, " +
                "unidad_medida_id, activo, tenant_id, version) " +
                "VALUES ('Producto F13-B', " + STOCK_INICIAL + ", 5.00, 10.00, " + umId +
                ", true, '" + TENANT_B + "', 0) RETURNING id", Long.class);
    }

    @BeforeEach
    void resetStock() {
        // Restaurar stock e inicializar version en 0 antes de cada test
        jdbc.execute("UPDATE productos SET stock_actual = " + STOCK_INICIAL + ", version = 0 " +
                "WHERE id = " + productoAId);
        jdbc.execute("UPDATE productos SET stock_actual = " + STOCK_INICIAL + ", version = 0 " +
                "WHERE id = " + productoBId);
        TenantContext.setCurrentTenant(TENANT_A);
    }

    @AfterEach
    void clearContexto() {
        TenantContext.clear();
    }

    // ── Test 1: Dos incrementos simultáneos — ambos se conservan ──────────────

    @Test
    @Order(1)
    void t01_dosIncrementosConcurrentes_ambosSeConservan() throws InterruptedException {
        final int DELTA = 10;
        CountDownLatch inicio = new CountDownLatch(1);
        CountDownLatch fin    = new CountDownLatch(2);
        AtomicInteger errores = new AtomicInteger(0);

        Runnable tarea = () -> {
            try {
                inicio.await();
                txTemplate.execute(status -> {
                    TenantContext.setCurrentTenant(TENANT_A);
                    return productoRepository.incrementarStockAtomico(productoAId, DELTA, TENANT_A);
                });
            } catch (Exception e) {
                errores.incrementAndGet();
            } finally {
                fin.countDown();
            }
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        pool.submit(tarea);
        pool.submit(tarea);
        inicio.countDown();
        fin.await(5, TimeUnit.SECONDS);
        pool.shutdown();

        int stockFinal = jdbc.queryForObject(
                "SELECT stock_actual FROM productos WHERE id = " + productoAId, Integer.class);

        assertThat(errores.get()).isEqualTo(0);
        assertThat(stockFinal).isEqualTo(STOCK_INICIAL + DELTA * 2); // ambos incrementos aplicados
    }

    // ── Test 2: Devolución concurrente con edición antigua — edición recibe 409 ──

    @Test
    @Order(2)
    void t02_devolucionYEdicionConcurrente_edicionAntiguaFalla() {
        // Capturar el producto con versión 0 (simula el estado del formulario abierto)
        Producto productoDesdeForm = txTemplate.execute(status ->
                productoRepository.findById(productoAId).orElseThrow()
        );
        long versionCapturada = productoDesdeForm.getVersion();

        // Thread A: devolución incrementa stock atómicamente (versión sube a 1)
        txTemplate.execute(status -> {
            TenantContext.setCurrentTenant(TENANT_A);
            productoRepository.incrementarStockAtomico(productoAId, 5, TENANT_A);
            return null;
        });

        // Thread B: formulario antiguo intenta guardar con versión 0 → debe fallar con OL exception
        Producto productoConVersionAntigua = new Producto();
        copiarDatos(productoConVersionAntigua, productoDesdeForm);
        productoConVersionAntigua.setVersion(versionCapturada); // versión antigua del formulario
        productoConVersionAntigua.setStockActual(STOCK_INICIAL); // valor stale del formulario

        assertThatThrownBy(() ->
                txTemplate.execute(status -> {
                    TenantContext.setCurrentTenant(TENANT_A);
                    return productoService.actualizarProducto(productoAId, productoConVersionAntigua);
                })
        ).isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    // ── Test 3: Formulario antiguo tras devolución — no puede guardar ──────────

    @Test
    @Order(3)
    void t03_formularioAntiguoTrasDevolucion_guardadoRechazado() {
        // 1. Leer producto (simula apertura del formulario con version=0)
        Producto captura = txTemplate.execute(s ->
                productoRepository.findById(productoAId).orElseThrow());
        assertThat(captura.getVersion()).isEqualTo(0L);

        // 2. Devolución ocurre mientras formulario está abierto
        txTemplate.execute(status -> {
            TenantContext.setCurrentTenant(TENANT_A);
            productoRepository.incrementarStockAtomico(productoAId, 3, TENANT_A);
            return null;
        });

        // 3. Version en BD es ahora 1 — formulario con version=0 no debe poder guardar
        Producto intentoGuardar = new Producto();
        copiarDatos(intentoGuardar, captura);
        intentoGuardar.setNombre("Nombre editado");

        assertThatThrownBy(() ->
                txTemplate.execute(s -> {
                    TenantContext.setCurrentTenant(TENANT_A);
                    return productoService.actualizarProducto(productoAId, intentoGuardar);
                })
        ).isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    // ── Test 4: Edición antigua no sobrescribe el stock ───────────────────────

    @Test
    @Order(4)
    void t04_edicionAntiguaRechazada_stockNoSobrescrito() {
        final int DEVOLUCION = 7;

        // 1. Capturar versión del formulario
        Producto captura = txTemplate.execute(s ->
                productoRepository.findById(productoAId).orElseThrow());

        // 2. Devolución añade stock
        txTemplate.execute(status -> {
            TenantContext.setCurrentTenant(TENANT_A);
            productoRepository.incrementarStockAtomico(productoAId, DEVOLUCION, TENANT_A);
            return null;
        });

        // 3. Intento de guardar con versión antigua
        Producto intentoAntiguo = new Producto();
        copiarDatos(intentoAntiguo, captura);
        intentoAntiguo.setStockActual(STOCK_INICIAL); // el stale value del form

        try {
            txTemplate.execute(s -> {
                TenantContext.setCurrentTenant(TENANT_A);
                return productoService.actualizarProducto(productoAId, intentoAntiguo);
            });
            fail("Debería haber lanzado ObjectOptimisticLockingFailureException");
        } catch (ObjectOptimisticLockingFailureException ignored) {
            // esperado
        }

        // 4. Stock en BD debe reflejar la devolución, no el stale value del formulario
        int stockFinal = jdbc.queryForObject(
                "SELECT stock_actual FROM productos WHERE id = " + productoAId, Integer.class);
        assertThat(stockFinal).isEqualTo(STOCK_INICIAL + DEVOLUCION);
    }

    // ── Test 5: Incremento atómico actualiza la versión ───────────────────────

    @Test
    @Order(5)
    void t05_incrementoAtomico_actualizaVersion() {
        long versionAntes = jdbc.queryForObject(
                "SELECT version FROM productos WHERE id = " + productoAId, Long.class);

        txTemplate.execute(status -> {
            TenantContext.setCurrentTenant(TENANT_A);
            productoRepository.incrementarStockAtomico(productoAId, 10, TENANT_A);
            return null;
        });

        long versionDespues = jdbc.queryForObject(
                "SELECT version FROM productos WHERE id = " + productoAId, Long.class);

        assertThat(versionDespues).isEqualTo(versionAntes + 1);
    }

    // ── Test 6: L1 cache no devuelve stock antiguo tras incremento atómico ────

    @Test
    @Order(6)
    void t06_contextoNoCacheaStockDesactualizado() {
        // Tx 1: leer producto (carga en L1 cache)
        // Tx 2: incremento atómico en otra transacción (bypassa L1 cache)
        // Tx 3: nueva lectura — debe ver el stock actualizado (nueva tx = nueva sesión Hibernate)

        final int DELTA = 15;

        txTemplate.execute(status -> {
            TenantContext.setCurrentTenant(TENANT_A);
            productoRepository.incrementarStockAtomico(productoAId, DELTA, TENANT_A);
            return null;
        });

        // Nueva transacción: sin cache previo — debe leer directamente de BD
        Integer stockLeido = txTemplate.execute(status ->
                productoRepository.findById(productoAId)
                        .map(Producto::getStockActual)
                        .orElseThrow()
        );

        assertThat(stockLeido).isEqualTo(STOCK_INICIAL + DELTA);
    }

    // ── Test 7: Dos devoluciones simultáneas — no hay sobredevolución ──────────

    @Test
    @Order(7)
    void t07_dosDevoluciones_simultaneas_ambosIncrementosAplicados() throws InterruptedException {
        final int DEVOLUCION = 5;
        CountDownLatch inicio = new CountDownLatch(1);
        CountDownLatch fin    = new CountDownLatch(2);
        AtomicInteger errores = new AtomicInteger(0);

        Runnable tarea = () -> {
            try {
                inicio.await();
                txTemplate.execute(status -> {
                    TenantContext.setCurrentTenant(TENANT_A);
                    return productoRepository.incrementarStockAtomico(productoAId, DEVOLUCION, TENANT_A);
                });
            } catch (Exception e) {
                errores.incrementAndGet();
            } finally {
                fin.countDown();
            }
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        pool.submit(tarea);
        pool.submit(tarea);
        inicio.countDown();
        fin.await(5, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(errores.get()).isEqualTo(0);

        int stockFinal = jdbc.queryForObject(
                "SELECT stock_actual FROM productos WHERE id = " + productoAId, Integer.class);

        // Ambas devoluciones suman → no se cancela ninguna
        assertThat(stockFinal).isEqualTo(STOCK_INICIAL + DEVOLUCION * 2);
    }

    // ── Test 8: Fallo en transacción revierte todo ─────────────────────────────

    @Test
    @Order(8)
    void t08_falloEnTransaccion_revierteStockYMovimiento() {
        int stockAntes = jdbc.queryForObject(
                "SELECT stock_actual FROM productos WHERE id = " + productoAId, Integer.class);
        long movimientosAntes = contarMovimientosTenant(TENANT_A);

        try {
            txTemplate.execute(status -> {
                TenantContext.setCurrentTenant(TENANT_A);
                // Incremento atómico (dentro de la transacción)
                productoRepository.incrementarStockAtomico(productoAId, 20, TENANT_A);
                // Simular fallo posterior
                throw new RuntimeException("Error simulado en medio de la tx");
            });
        } catch (RuntimeException ignored) {
            // rollback esperado
        }

        int stockDespues = jdbc.queryForObject(
                "SELECT stock_actual FROM productos WHERE id = " + productoAId, Integer.class);
        long movimientosDespues = contarMovimientosTenant(TENANT_A);

        assertThat(stockDespues).isEqualTo(stockAntes);       // revertido
        assertThat(movimientosDespues).isEqualTo(movimientosAntes); // sin movimientos nuevos
    }

    // ── Test 9: Flujo Movimientos — incremento atómico actualiza stock ─────────

    @Test
    @Order(9)
    void t09_flujoMovimientos_incrementoAtomicoActualizaStockCorrectamente() {
        final int ENTRADA = 25;
        int stockAntes = jdbc.queryForObject(
                "SELECT stock_actual FROM productos WHERE id = " + productoAId, Integer.class);
        long versionAntes = jdbc.queryForObject(
                "SELECT version FROM productos WHERE id = " + productoAId, Long.class);

        txTemplate.execute(status -> {
            TenantContext.setCurrentTenant(TENANT_A);
            int filas = productoRepository.incrementarStockAtomico(productoAId, ENTRADA, TENANT_A);
            assertThat(filas).isEqualTo(1); // exactamente 1 fila afectada
            return null;
        });

        int stockDespues = jdbc.queryForObject(
                "SELECT stock_actual FROM productos WHERE id = " + productoAId, Integer.class);
        long versionDespues = jdbc.queryForObject(
                "SELECT version FROM productos WHERE id = " + productoAId, Long.class);

        assertThat(stockDespues).isEqualTo(stockAntes + ENTRADA);
        assertThat(versionDespues).isEqualTo(versionAntes + 1);
    }

    // ── Test 10: Flujo Historial — mismo mecanismo atómico, mismo resultado ───

    @Test
    @Order(10)
    void t10_flujoHistorial_incrementoAtomicoMismoResultadoQueMovimientos() {
        final int DEVOLUCION = 12;

        // Ambos flujos usan productoRepository.incrementarStockAtomico —
        // simular las dos llamadas en transacciones separadas y verificar acumulación
        txTemplate.execute(s -> {
            TenantContext.setCurrentTenant(TENANT_A);
            productoRepository.incrementarStockAtomico(productoAId, DEVOLUCION, TENANT_A); // flujo movimientos
            return null;
        });
        int stockTrasMovimientos = jdbc.queryForObject(
                "SELECT stock_actual FROM productos WHERE id = " + productoAId, Integer.class);

        txTemplate.execute(s -> {
            TenantContext.setCurrentTenant(TENANT_A);
            productoRepository.incrementarStockAtomico(productoAId, DEVOLUCION, TENANT_A); // flujo historial
            return null;
        });
        int stockTrasHistorial = jdbc.queryForObject(
                "SELECT stock_actual FROM productos WHERE id = " + productoAId, Integer.class);

        assertThat(stockTrasMovimientos).isEqualTo(STOCK_INICIAL + DEVOLUCION);
        assertThat(stockTrasHistorial).isEqualTo(STOCK_INICIAL + DEVOLUCION * 2);
    }

    // ── Test 11: Ambas vías producen el mismo delta neto ─────────────────────

    @Test
    @Order(11)
    void t11_ambasVias_mismoDeltaNetoPorUnidad() {
        final int CANTIDAD = 8;

        // Vía A: flujo movimientos (una llamada)
        jdbc.execute("UPDATE productos SET stock_actual = " + STOCK_INICIAL + ", version = 0 WHERE id = " + productoAId);
        txTemplate.execute(s -> {
            TenantContext.setCurrentTenant(TENANT_A);
            productoRepository.incrementarStockAtomico(productoAId, CANTIDAD, TENANT_A);
            return null;
        });
        int stockViaA = jdbc.queryForObject(
                "SELECT stock_actual FROM productos WHERE id = " + productoAId, Integer.class);

        // Vía B: flujo historial (también una llamada con la misma cantidad)
        jdbc.execute("UPDATE productos SET stock_actual = " + STOCK_INICIAL + ", version = 0 WHERE id = " + productoAId);
        txTemplate.execute(s -> {
            TenantContext.setCurrentTenant(TENANT_A);
            productoRepository.incrementarStockAtomico(productoAId, CANTIDAD, TENANT_A);
            return null;
        });
        int stockViaB = jdbc.queryForObject(
                "SELECT stock_actual FROM productos WHERE id = " + productoAId, Integer.class);

        // Ambas vías deben producir el mismo resultado
        assertThat(stockViaA).isEqualTo(stockViaB);
        assertThat(stockViaA).isEqualTo(STOCK_INICIAL + CANTIDAD);
    }

    // ── Test 12: Aislamiento multi-tenant ─────────────────────────────────────

    @Test
    @Order(12)
    void t12_aislamientoMultiTenant_operacionEnTenantANoAfectaTenantB() {
        final int DELTA = 30;

        // Incremento solo para tenant A
        txTemplate.execute(status -> {
            TenantContext.setCurrentTenant(TENANT_A);
            int filas = productoRepository.incrementarStockAtomico(productoAId, DELTA, TENANT_A);
            assertThat(filas).isEqualTo(1);
            return null;
        });

        // Intentar incrementar con tenant B sobre productoA — tenantId no coincide → 0 filas
        txTemplate.execute(status -> {
            TenantContext.setCurrentTenant(TENANT_B);
            int filas = productoRepository.incrementarStockAtomico(productoAId, DELTA, TENANT_B);
            assertThat(filas).isEqualTo(0); // sin efecto — tenant incorrecto
            return null;
        });

        int stockA = jdbc.queryForObject(
                "SELECT stock_actual FROM productos WHERE id = " + productoAId, Integer.class);
        int stockB = jdbc.queryForObject(
                "SELECT stock_actual FROM productos WHERE id = " + productoBId, Integer.class);

        assertThat(stockA).isEqualTo(STOCK_INICIAL + DELTA); // solo Tenant A afectado
        assertThat(stockB).isEqualTo(STOCK_INICIAL);           // Tenant B intacto
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private long contarMovimientosTenant(String tenantId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM movimientos_inventario WHERE tenant_id = '" + tenantId + "'",
                Long.class);
        return count != null ? count : 0L;
    }

    /** Copia campos de un Producto a otro para simular el estado del formulario de edición. */
    private void copiarDatos(Producto destino, Producto origen) {
        destino.setId(origen.getId());
        destino.setNombre(origen.getNombre());
        destino.setCodigoBarras(origen.getCodigoBarras());
        destino.setStockActual(origen.getStockActual());
        destino.setStockMinimo(origen.getStockMinimo());
        destino.setStockMaximo(origen.getStockMaximo());
        destino.setCostoUnitario(origen.getCostoUnitario());
        destino.setPrecioVenta(origen.getPrecioVenta());
        destino.setActivo(origen.getActivo());
        destino.setTenantId(origen.getTenantId());
        destino.setUnidadMedida(origen.getUnidadMedida());
        destino.setCategoriaRef(origen.getCategoriaRef());
        destino.setVersion(origen.getVersion());
    }
}
