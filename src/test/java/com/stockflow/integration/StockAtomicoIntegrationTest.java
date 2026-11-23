package com.stockflow.integration;

import com.stockflow.repository.ProductoRepository;
import com.stockflow.util.TenantContext;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * F-13 — Tests de concurrencia para decrementarStockAtomico / incrementarStockAtomico.
 * PostgreSQL 16 real (Testcontainers). Sin mocks.
 *
 * tc1: 2 ventas concurrentes, suma ≤ stock → ambas OK, stock correcto
 * tc2: 2 ventas concurrentes, suma > stock → exactamente una OK, stock ≥ 0
 * tc3: stock insuficiente desde el inicio → UPDATE devuelve 0, stock sin cambios
 * tc4: aislamiento multi-tenant → decremento en tenant-X no afecta tenant-Y
 * tc5: consistencia producto/lotes tras incremento+decremento secuencial
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StockAtomicoIntegrationTest {

    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    static {
        postgres.start();
    }

    @Autowired private ProductoRepository productoRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate txTemplate;

    private static final String TENANT_A = "sat-a";
    private static final String TENANT_B = "sat-b";

    private Long umId;

    @BeforeAll
    void setup() {
        jdbc.execute("INSERT INTO tenants (tenant_id, nombre, activo, rubro) VALUES ('" + TENANT_A + "', 'SAT A', true, 'OTRO') ON CONFLICT (tenant_id) DO NOTHING");
        jdbc.execute("INSERT INTO tenants (tenant_id, nombre, activo, rubro) VALUES ('" + TENANT_B + "', 'SAT B', true, 'OTRO') ON CONFLICT (tenant_id) DO NOTHING");
        umId = jdbc.queryForObject("SELECT id FROM unidad_medida WHERE tenant_id IS NULL LIMIT 1", Long.class);
    }

    @BeforeEach
    void setTenant() {
        TenantContext.setCurrentTenant(TENANT_A);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private Long crearProducto(String nombre, int stock, String tenant) {
        return jdbc.queryForObject(
                "INSERT INTO productos (nombre, stock_actual, costo_unitario, precio_venta, unidad_medida_id, activo, tenant_id, version) " +
                "VALUES ('" + nombre + "', " + stock + ", 5.00, 10.00, " + umId + ", true, '" + tenant + "', 0) RETURNING id",
                Long.class);
    }

    private int getStock(Long id) {
        return jdbc.queryForObject("SELECT stock_actual FROM productos WHERE id = " + id, Integer.class);
    }

    // ── tc1: 2 ventas concurrentes, suma ≤ stock ─────────────────────────────

    @Test
    @Order(1)
    void tc1_dosVentasConcurrentes_sumaLeStock_ambasOK() throws Exception {
        Long prodId = crearProducto("SAT-tc1", 10, TENANT_A);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch listo = new CountDownLatch(2);
        AtomicInteger exitos = new AtomicInteger(0);

        Callable<Integer> tarea = () -> {
            listo.countDown();
            listo.await();
            return txTemplate.execute(status ->
                    productoRepository.decrementarStockAtomico(prodId, 4, TENANT_A));
        };

        List<Future<Integer>> futures = pool.invokeAll(List.of(tarea, tarea));
        pool.shutdown();

        for (Future<Integer> f : futures) {
            if (f.get() == 1) exitos.incrementAndGet();
        }

        assertThat(exitos.get()).as("ambas ventas deben completarse (4+4 ≤ 10)").isEqualTo(2);
        assertThat(getStock(prodId)).as("10 - 4 - 4 = 2").isEqualTo(2);
    }

    // ── tc2: 2 ventas concurrentes, suma > stock ─────────────────────────────

    @Test
    @Order(2)
    void tc2_dosVentasConcurrentes_sumaMayorStock_soloUnaOK() throws Exception {
        Long prodId = crearProducto("SAT-tc2", 5, TENANT_A);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch listo = new CountDownLatch(2);
        AtomicInteger exitos = new AtomicInteger(0);

        Callable<Integer> tarea = () -> {
            listo.countDown();
            listo.await();
            return txTemplate.execute(status ->
                    productoRepository.decrementarStockAtomico(prodId, 4, TENANT_A));
        };

        List<Future<Integer>> futures = pool.invokeAll(List.of(tarea, tarea));
        pool.shutdown();

        for (Future<Integer> f : futures) {
            if (f.get() == 1) exitos.incrementAndGet();
        }

        assertThat(exitos.get()).as("solo una de las dos ventas debe completarse (4+4 > 5)").isEqualTo(1);
        assertThat(getStock(prodId)).as("stock = 5 - 4 = 1, nunca negativo").isEqualTo(1);
    }

    // ── tc3: stock insuficiente desde el inicio ───────────────────────────────

    @Test
    @Order(3)
    void tc3_stockInsuficiente_returnsCero_stockSinCambios() {
        Long prodId = crearProducto("SAT-tc3", 2, TENANT_A);

        int filas = txTemplate.execute(status ->
                productoRepository.decrementarStockAtomico(prodId, 5, TENANT_A));

        assertThat(filas).as("UPDATE debe devolver 0 cuando stockActual < delta").isEqualTo(0);
        assertThat(getStock(prodId)).as("stock no debe cambiar").isEqualTo(2);
    }

    // ── tc4: aislamiento multi-tenant ─────────────────────────────────────────

    @Test
    @Order(4)
    void tc4_multiTenant_decrementoEnATenantNoAfectaBTenant() {
        Long prodA = crearProducto("SAT-tc4-a", 10, TENANT_A);
        Long prodB = crearProducto("SAT-tc4-b", 10, TENANT_B);

        // Intentar decrementar prod de TENANT_B usando TENANT_A → debe fallar
        int filasConTenantIncorrecto = txTemplate.execute(status ->
                productoRepository.decrementarStockAtomico(prodB, 3, TENANT_A));

        // Decrementar correctamente en TENANT_A
        int filasA = txTemplate.execute(status ->
                productoRepository.decrementarStockAtomico(prodA, 3, TENANT_A));

        assertThat(filasConTenantIncorrecto).as("tenant incorrecto → 0 filas afectadas").isEqualTo(0);
        assertThat(getStock(prodB)).as("stock de TENANT_B no debe cambiar").isEqualTo(10);
        assertThat(filasA).as("decremento en TENANT_A debe completarse").isEqualTo(1);
        assertThat(getStock(prodA)).as("10 - 3 = 7").isEqualTo(7);
    }

    // ── tc5: consistencia incremento + decremento secuencial ─────────────────

    @Test
    @Order(5)
    void tc5_incrementoYDecremento_stockConsistente() {
        Long prodId = crearProducto("SAT-tc5", 20, TENANT_A);

        // Simular venta (-8) + devolución (+3) + nueva venta (-5)
        txTemplate.execute(s -> productoRepository.decrementarStockAtomico(prodId, 8, TENANT_A));
        txTemplate.execute(s -> productoRepository.incrementarStockAtomico(prodId, 3, TENANT_A));
        txTemplate.execute(s -> productoRepository.decrementarStockAtomico(prodId, 5, TENANT_A));

        assertThat(getStock(prodId)).as("20 - 8 + 3 - 5 = 10").isEqualTo(10);

        // Verificar que version incrementó (3 operaciones atómicas)
        Integer version = jdbc.queryForObject(
                "SELECT version FROM productos WHERE id = " + prodId, Integer.class);
        assertThat(version).as("version debe ser 3 (una por operación atómica)").isEqualTo(3);
    }
}
