package com.stockflow.integration;

import com.stockflow.dto.RegistrarConsumoOtRequestDTO;
import com.stockflow.exception.BadRequestException;
import com.stockflow.repository.MovimientoInventarioRepository;
import com.stockflow.repository.OtConsumoLoteRepository;
import com.stockflow.repository.StockLoteRepository;
import com.stockflow.service.OtConsumoService;
import com.stockflow.service.TenantService;
import com.stockflow.util.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * Pruebas de integración para OtConsumoService con PostgreSQL real (Testcontainers).
 *
 * FT-5: Dos consumos concurrentes sobre el mismo lote no producen stock negativo.
 * FT-6: Una excepción en saveAll(consumoLotes) revierte todos los cambios de stock.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OtConsumoIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private OtConsumoService otConsumoService;

    @Autowired
    private StockLoteRepository stockLoteRepository;

    @Autowired
    private MovimientoInventarioRepository movimientoRepository;

    @MockBean
    private OtConsumoLoteRepository consumoLoteRepository;

    @MockBean
    private TenantService tenantService;

    @Autowired
    private JdbcTemplate jdbc;

    private static final String TENANT_ID = "tc-integ-001";
    private static final Long   USER_ID   = 98001L;
    private static final int    STOCK_INICIAL_LOTE = 10;

    private Long productoId;
    private Long loteId;
    private Long otId;
    private Long otId2;

    // ── Setup ─────────────────────────────────────────────────────────────────

    @BeforeAll
    void insertarDatosBase() {
        jdbc.execute("INSERT INTO tenants (tenant_id, nombre, activo, rubro) " +
                "VALUES ('" + TENANT_ID + "', 'Empresa Test IT', true, 'EMPRESA_SERVICIOS') " +
                "ON CONFLICT (tenant_id) DO NOTHING");

        Long rolId = jdbc.queryForObject(
                "SELECT id FROM roles WHERE nombre = 'ADMIN' LIMIT 1", Long.class);

        jdbc.execute("INSERT INTO usuarios (id, email, contraseña, nombre, rol_id, activo, tenant_id) " +
                "VALUES (" + USER_ID + ", 'ituser@test.com', 'irrelevante', 'IT User', " +
                rolId + ", true, '" + TENANT_ID + "') ON CONFLICT (id) DO NOTHING");

        Long umId = jdbc.queryForObject(
                "SELECT id FROM unidad_medida WHERE tenant_id IS NULL LIMIT 1", Long.class);

        productoId = jdbc.queryForObject(
                "INSERT INTO productos (nombre, stock_actual, costo_unitario, precio_venta, unidad_medida_id, activo, tenant_id) " +
                "VALUES ('Repuesto IT', 50, 10.00, 20.00, " + umId + ", true, '" + TENANT_ID + "') RETURNING id",
                Long.class);

        Long movId = jdbc.queryForObject(
                "INSERT INTO movimientos_inventario (producto_id, cantidad, tipo, usuario_id, tenant_id) " +
                "VALUES (" + productoId + ", 50, 'COMPRA', " + USER_ID + ", '" + TENANT_ID + "') RETURNING id",
                Long.class);

        // sucursal_id ficticio (99901) — ordenes_trabajo no tiene FK a sucursales
        loteId = jdbc.queryForObject(
                "INSERT INTO stock_lotes (tenant_id, movimiento_id, producto_id, sucursal_id, " +
                "fecha_vencimiento, stock_inicial, stock_actual) " +
                "VALUES ('" + TENANT_ID + "', " + movId + ", " + productoId + ", 99901, " +
                "'2035-12-31', 50, " + STOCK_INICIAL_LOTE + ") RETURNING id",
                Long.class);

        Long clienteId = jdbc.queryForObject(
                "INSERT INTO clientes (nombre, tenant_id) " +
                "VALUES ('Cliente IT', '" + TENANT_ID + "') RETURNING id",
                Long.class);

        Long equipoId = jdbc.queryForObject(
                "INSERT INTO equipos_clientes (tenant_id, cliente_id, nombre) " +
                "VALUES ('" + TENANT_ID + "', " + clienteId + ", 'Equipo IT') RETURNING id",
                Long.class);

        otId = jdbc.queryForObject(
                "INSERT INTO ordenes_trabajo (tenant_id, numero_ot, anio, equipo_cliente_id, " +
                "sucursal_id, cliente_id, estado, prioridad, descripcion_problema) " +
                "VALUES ('" + TENANT_ID + "', 'OT-IT-001', 2026, " + equipoId + ", " +
                "99901, " + clienteId + ", 'EN_PROCESO', 'NORMAL', 'Test concurrencia') RETURNING id",
                Long.class);

        otId2 = jdbc.queryForObject(
                "INSERT INTO ordenes_trabajo (tenant_id, numero_ot, anio, equipo_cliente_id, " +
                "sucursal_id, cliente_id, estado, prioridad, descripcion_problema) " +
                "VALUES ('" + TENANT_ID + "', 'OT-IT-002', 2026, " + equipoId + ", " +
                "99901, " + clienteId + ", 'EN_PROCESO', 'NORMAL', 'Test rollback') RETURNING id",
                Long.class);
    }

    @BeforeEach
    void resetEstadoYContexto() {
        TenantContext.setCurrentTenant(TENANT_ID);
        TenantContext.setCurrentUserId(USER_ID);
        // Limpiar registros del test anterior
        jdbc.execute("DELETE FROM ot_consumo_material WHERE tenant_id = '" + TENANT_ID + "'");
        jdbc.execute("DELETE FROM movimientos_inventario WHERE tenant_id = '" + TENANT_ID + "' AND tipo = 'CONSUMO_OT'");
        // Restaurar stock
        jdbc.execute("UPDATE stock_lotes SET stock_actual = " + STOCK_INICIAL_LOTE + " WHERE id = " + loteId);
        jdbc.execute("UPDATE productos SET stock_actual = 50 WHERE id = " + productoId);
        Mockito.reset(consumoLoteRepository);
    }

    @AfterEach
    void clearContexto() {
        TenantContext.clear();
    }

    // ── FT-5: Dos consumos concurrentes no generan stock negativo ─────────────

    @Test
    void ft5_dosConcurrentesEnMismoLote_noGeneranStockNegativo() throws InterruptedException {
        final int CANTIDAD = 6; // 6 de 10 disponibles → solo 1 puede tener éxito
        RegistrarConsumoOtRequestDTO req1 = RegistrarConsumoOtRequestDTO.builder()
                .productoId(productoId).cantidadPresentacion(CANTIDAD).build();
        RegistrarConsumoOtRequestDTO req2 = RegistrarConsumoOtRequestDTO.builder()
                .productoId(productoId).cantidadPresentacion(CANTIDAD).build();

        CountDownLatch inicio = new CountDownLatch(1);
        CountDownLatch fin    = new CountDownLatch(2);

        List<Exception>   excepciones  = new CopyOnWriteArrayList<>();
        AtomicInteger     exitosos     = new AtomicInteger(0);

        final Long ot1 = otId;
        final Long ot2 = otId2;

        Thread t1 = new Thread(() -> {
            TenantContext.setCurrentTenant(TENANT_ID);
            TenantContext.setCurrentUserId(USER_ID);
            try {
                inicio.await();
                otConsumoService.registrarConsumo(ot1, req1);
                exitosos.incrementAndGet();
            } catch (Exception e) {
                excepciones.add(e);
            } finally {
                TenantContext.clear();
                fin.countDown();
            }
        });

        Thread t2 = new Thread(() -> {
            TenantContext.setCurrentTenant(TENANT_ID);
            TenantContext.setCurrentUserId(USER_ID);
            try {
                inicio.await();
                otConsumoService.registrarConsumo(ot2, req2);
                exitosos.incrementAndGet();
            } catch (Exception e) {
                excepciones.add(e);
            } finally {
                TenantContext.clear();
                fin.countDown();
            }
        });

        t1.start();
        t2.start();
        inicio.countDown();
        boolean terminaron = fin.await(20, TimeUnit.SECONDS);

        assertThat(terminaron).as("Los dos hilos deben terminar en 20s").isTrue();

        int stockFinal = jdbc.queryForObject(
                "SELECT stock_actual FROM stock_lotes WHERE id = " + loteId, Integer.class);

        assertThat(stockFinal).as("El stock del lote no puede ser negativo").isGreaterThanOrEqualTo(0);
        assertThat(exitosos.get()).as("Exactamente un consumo debe tener éxito").isEqualTo(1);
        assertThat(excepciones).as("El otro consumo debe lanzar excepción").hasSize(1);
        assertThat(excepciones.get(0))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("insuficiente");
    }

    // ── FT-6: Excepción en saveAll revierte todos los cambios de stock ────────

    @Test
    void ft6_exceptionEnSaveAll_rollbackCompletoDeTodosLosStocks() {
        doThrow(new RuntimeException("Fallo simulado en saveAll"))
                .when(consumoLoteRepository).saveAll(any());

        RegistrarConsumoOtRequestDTO req = RegistrarConsumoOtRequestDTO.builder()
                .productoId(productoId).cantidadPresentacion(5).build();

        assertThatThrownBy(() -> otConsumoService.registrarConsumo(otId, req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Fallo simulado");

        // Verificar rollback en base de datos real
        int stockLoteFinal = jdbc.queryForObject(
                "SELECT stock_actual FROM stock_lotes WHERE id = " + loteId, Integer.class);
        assertThat(stockLoteFinal)
                .as("El stock del lote debe ser el original tras el rollback")
                .isEqualTo(STOCK_INICIAL_LOTE);

        int stockProductoFinal = jdbc.queryForObject(
                "SELECT stock_actual FROM productos WHERE id = " + productoId, Integer.class);
        assertThat(stockProductoFinal)
                .as("El stock del producto debe ser el original tras el rollback")
                .isEqualTo(50);

        Integer movCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM movimientos_inventario WHERE tenant_id = '" + TENANT_ID +
                "' AND tipo = 'CONSUMO_OT'", Integer.class);
        assertThat(movCount)
                .as("No debe quedar ningún movimiento de inventario tras el rollback")
                .isZero();
    }
}
