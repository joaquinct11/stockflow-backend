package com.stockflow.service;

import com.stockflow.dto.DashboardSummaryDTO;
import com.stockflow.util.TenantContext;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.*;

/**
 * Validación del desglose de stock bajo en el dashboard:
 * sinStockCount (stockActual ≤ 0) y stockBajoMinimoCount (0 < stockActual < stockMinimo).
 *
 * <p>Causa raíz: el frontend derivaba el desglose leyendo {@code bajoStockItems} (limitado a 5),
 * obteniendo 3 sin stock y 2 bajo mínimo de los 5 mostrados en lugar de los 16/2 reales.
 * El fix calcula {@code sinStockCount} y {@code stockBajoMinimoCount} en el service,
 * sobre la lista completa antes del truncado.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Testcontainers
@DisplayName("F-12 — Dashboard stock breakdown (sinStock vs bajoMínimo)")
class DashboardStockBreakdownTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    static {
        postgres.start();
    }

    @Autowired private ProductoService productoService;
    @Autowired private JdbcTemplate    jdbc;

    // Tenant propio para este test, sin interferencia con otros suites
    private static final String TENANT  = "dsbd-breakdown-tenant-01";
    private static final Long   USER_ID = 92_000L;
    private static final String TENANT2 = "dsbd-breakdown-tenant-02"; // aislamiento

    // IDs de productos (rango 92_1xx para no colisionar)
    // 16 sin stock (stockActual = 0, stockMinimo = 5)
    // 2  bajo mínimo (stockActual > 0, stockActual < stockMinimo)
    // 1  stock normal (no debe contarse)
    // 1  inactivo con stock bajo (no debe contarse)
    private static final int SIN_STOCK_COUNT   = 16;
    private static final int BAJO_MINIMO_COUNT = 2;
    private Long unidadMedidaId;
    private static final long ID_BASE_SIN_STOCK   = 92_100L;
    private static final long ID_BASE_BAJO_MINIMO = 92_200L;
    private static final long ID_NORMAL           = 92_300L;
    private static final long ID_INACTIVO         = 92_301L;
    private static final long ID_OTRO_TENANT      = 92_302L;

    @BeforeAll
    void setup() {
        Long rolAdminId = jdbc.queryForObject(
                "SELECT id FROM roles WHERE nombre = 'ADMIN' LIMIT 1", Long.class);
        unidadMedidaId = jdbc.queryForObject(
                "SELECT id FROM unidad_medida WHERE tenant_id IS NULL LIMIT 1", Long.class);

        for (String t : new String[]{TENANT, TENANT2}) {
            jdbc.update("""
                INSERT INTO tenants (tenant_id, nombre, activo, rubro)
                VALUES (?, ?, true, 'FERRETERIA')
                ON CONFLICT (tenant_id) DO NOTHING
                """, t, "Bodega-" + t);
        }

        jdbc.update("""
            INSERT INTO usuarios (id, email, contraseña, nombre, apellido, activo)
            VALUES (?, 'dsbd.admin@test.com', 'x', 'Admin', 'DSBD', true)
            ON CONFLICT (id) DO NOTHING
            """, USER_ID);
        jdbc.update("""
            INSERT INTO usuario_tenant (usuario_id, tenant_id, rol_id, activo)
            VALUES (?, ?, ?, true)
            ON CONFLICT (usuario_id, tenant_id) DO NOTHING
            """, USER_ID, TENANT, rolAdminId);
        jdbc.update("""
            INSERT INTO suscripciones (usuario_principal_id, tenant_id, plan_id, precio_mensual, estado, created_at)
            SELECT ?, ?, 'PRO', 0.00, 'ACTIVA', NOW()
            WHERE NOT EXISTS (SELECT 1 FROM suscripciones WHERE tenant_id = ?)
            """, USER_ID, TENANT, TENANT);

        // 16 productos sin stock (stockActual = 0, stockMinimo = 5) → entra en bajoStock porque 0 < 5
        for (int i = 0; i < SIN_STOCK_COUNT; i++) {
            insertProducto(ID_BASE_SIN_STOCK + i, "SinStock-" + i, TENANT, true, 0, 5);
        }

        // 2 productos con stock positivo pero bajo mínimo
        insertProducto(ID_BASE_BAJO_MINIMO,     "BajoMin-A", TENANT, true, 3, 10);
        insertProducto(ID_BASE_BAJO_MINIMO + 1, "BajoMin-B", TENANT, true, 1,  5);

        // 1 producto activo con stock normal (no debe aparecer)
        insertProducto(ID_NORMAL, "Normal-01", TENANT, true, 20, 5);

        // 1 producto INACTIVO con stock bajo (no debe contarse)
        insertProducto(ID_INACTIVO, "Inactivo-01", TENANT, false, 0, 5);

        // 1 producto de otro tenant (no debe aparecer en TENANT)
        insertProducto(ID_OTRO_TENANT, "OtroTenant-01", TENANT2, true, 0, 5);
    }

    private void insertProducto(long id, String nombre, String tenantId,
                                boolean activo, int stock, int minimo) {
        jdbc.update("""
            INSERT INTO productos (id, nombre, tenant_id, activo, stock_actual, stock_minimo, precio_venta, costo_unitario, unidad_medida_id, tipo, created_at)
            VALUES (?, ?, ?, ?, ?, ?, 10.00, 0.00, ?, 'PRODUCTO', NOW())
            ON CONFLICT (id) DO NOTHING
            """, id, nombre, tenantId, activo, stock, minimo, unidadMedidaId);
    }

    @BeforeEach
    void setContext() {
        TenantContext.setCurrentTenant(TENANT);
        TenantContext.setCurrentUserId(USER_ID);
    }

    @AfterAll
    void cleanup() {
        TenantContext.clear();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DSB-1: sinStockCount == 16
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("DSB-1: sinStockCount devuelve 16 (productos activos con stockActual <= 0)")
    void sinStockCount_es16() {
        DashboardSummaryDTO summary = productoService.getDashboardSummary(TENANT, null);
        assertThat(summary.getSinStockCount())
                .as("sinStockCount debe ser %d", SIN_STOCK_COUNT)
                .isEqualTo(SIN_STOCK_COUNT);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DSB-2: stockBajoMinimoCount == 2
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(2)
    @DisplayName("DSB-2: stockBajoMinimoCount devuelve 2 (activos con 0 < stockActual < stockMinimo)")
    void stockBajoMinimoCount_es2() {
        DashboardSummaryDTO summary = productoService.getDashboardSummary(TENANT, null);
        assertThat(summary.getStockBajoMinimoCount())
                .as("stockBajoMinimoCount debe ser %d", BAJO_MINIMO_COUNT)
                .isEqualTo(BAJO_MINIMO_COUNT);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DSB-3: bajoStockCount total == 18
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(3)
    @DisplayName("DSB-3: bajoStockCount == sinStockCount + stockBajoMinimoCount == 18")
    void bajoStockCount_esTotal() {
        DashboardSummaryDTO summary = productoService.getDashboardSummary(TENANT, null);
        int expected = SIN_STOCK_COUNT + BAJO_MINIMO_COUNT;
        assertThat(summary.getBajoStockCount())
                .as("bajoStockCount debe ser %d", expected)
                .isEqualTo(expected);
        assertThat(summary.getSinStockCount() + summary.getStockBajoMinimoCount())
                .as("sinStockCount + stockBajoMinimoCount debe sumar al total")
                .isEqualTo(summary.getBajoStockCount());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DSB-4: productos inactivos excluidos de los tres contadores
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(4)
    @DisplayName("DSB-4: productos inactivos no afectan ningún contador")
    void inactivos_excluidos() {
        DashboardSummaryDTO summary = productoService.getDashboardSummary(TENANT, null);

        // Si incluyera el inactivo, sinStockCount sería 17
        assertThat(summary.getSinStockCount())
                .as("El producto inactivo ID_INACTIVO no debe sumarse a sinStockCount")
                .isEqualTo(SIN_STOCK_COUNT);  // 16, no 17
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DSB-5: productos con stock normal no se cuentan
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(5)
    @DisplayName("DSB-5: producto activo con stock normal no aparece en ningún contador de bajo stock")
    void normal_excluido() {
        DashboardSummaryDTO summary = productoService.getDashboardSummary(TENANT, null);

        boolean incluyeNormal = summary.getBajoStockItems().stream()
                .anyMatch(item -> ID_NORMAL == item.getId());
        assertThat(incluyeNormal)
                .as("Producto con stock normal no debe aparecer en bajoStockItems")
                .isFalse();
        // bajoStockCount no incluye el normal (ya cubierto por DSB-3)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DSB-6: aislamiento — tenant2 no contamina los contadores de TENANT
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(6)
    @DisplayName("DSB-6: aislamiento — producto de TENANT2 no afecta contadores de TENANT")
    void aislamientoTenant() {
        DashboardSummaryDTO summaryT1 = productoService.getDashboardSummary(TENANT,  null);
        DashboardSummaryDTO summaryT2 = productoService.getDashboardSummary(TENANT2, null);

        // TENANT2 tiene 1 producto sin stock
        assertThat(summaryT2.getSinStockCount())
                .as("TENANT2 debe tener 1 producto sin stock")
                .isEqualTo(1);

        // TENANT no ve los productos de TENANT2
        assertThat(summaryT1.getBajoStockCount())
                .as("TENANT no debe incluir el producto de TENANT2")
                .isEqualTo(SIN_STOCK_COUNT + BAJO_MINIMO_COUNT);
    }
}
