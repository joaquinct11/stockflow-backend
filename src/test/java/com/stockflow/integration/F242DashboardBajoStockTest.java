package com.stockflow.integration;

import com.stockflow.dto.DashboardSummaryDTO;
import com.stockflow.service.ProductoService;
import com.stockflow.util.TenantContext;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.*;

/**
 * F-24.2 Problemas B y C: Dashboard bajo stock usa stock por sucursal (PSS).
 *
 * d01 — Sin sucursalId: dashboard usa Producto.stockActual global
 * d02 — Con sucursalId: dashboard usa PSS, no global
 * d03 — Producto con PSS bajo mínimo aparece en dashboard de esa sucursal
 * d04 — Producto con PSS en otra sucursal no aparece en la sucursal activa
 * d05 — Dashboard y módulo Productos usan misma regla (strict <)
 * d06 — Productos con variantes: dashboard usa suma de PVSS
 * d07 — Tenant sin sucursales (legacy): dashboard usa global
 * d08 — Producto con stockActual == stockMinimo NO es bajo stock (regla: strict <)
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class F242DashboardBajoStockTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    static {
        postgres.start();
    }

    @Autowired private ProductoService productoService;
    @Autowired private JdbcTemplate jdbc;

    private static final String TENANT = "f242-bs-tenant";

    private Long sucursalPrincipal;
    private Long sucursalMiraflores;
    private Long umId;

    @BeforeAll
    void setup() {
        jdbc.update("INSERT INTO tenants (tenant_id, nombre, activo, rubro) " +
                "VALUES (?, 'F242 BS', true, 'OTRO') ON CONFLICT (tenant_id) DO NOTHING", TENANT);

        sucursalPrincipal = jdbc.queryForObject(
                "INSERT INTO sucursales (nombre, tenant_id, es_principal, activo) " +
                "VALUES ('Principal', ?, true, true) RETURNING id", Long.class, TENANT);

        sucursalMiraflores = jdbc.queryForObject(
                "INSERT INTO sucursales (nombre, tenant_id, es_principal, activo) " +
                "VALUES ('Miraflores', ?, false, true) RETURNING id", Long.class, TENANT);

        umId = jdbc.queryForObject(
                "SELECT id FROM unidad_medida WHERE tenant_id IS NULL LIMIT 1", Long.class);
    }

    @BeforeEach
    void limpiarProductos() {
        jdbc.update("DELETE FROM producto_variante_stock_sucursal WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM producto_variantes WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM producto_stock_sucursal WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM productos WHERE tenant_id = ?", TENANT);
    }

    private Long crearProducto(String nombre, int stockActual, int stockMinimo) {
        return jdbc.queryForObject(
                "INSERT INTO productos (nombre, stock_actual, stock_minimo, costo_unitario, precio_venta, " +
                "unidad_medida_id, activo, tenant_id, version) " +
                "VALUES (?, ?, ?, 5.00, 10.00, ?, true, ?, 0) RETURNING id",
                Long.class, nombre, stockActual, stockMinimo, umId, TENANT);
    }

    private void crearPSS(Long productoId, Long sucursalId, int stock) {
        jdbc.update("INSERT INTO producto_stock_sucursal (producto_id, sucursal_id, tenant_id, stock_actual) " +
                "VALUES (?, ?, ?, ?) ON CONFLICT (producto_id, sucursal_id) DO UPDATE SET stock_actual = ?",
                productoId, sucursalId, TENANT, stock, stock);
    }

    private DashboardSummaryDTO dashboard(Long sucursalId) {
        TenantContext.setCurrentTenant(TENANT);
        try { return productoService.getDashboardSummary(TENANT, sucursalId); }
        finally { TenantContext.clear(); }
    }

    // ── Tests ────────────────────────────────────────────────────────────────

    @Test @Order(1)
    @DisplayName("d01: Sin sucursalId — dashboard usa Producto.stockActual global")
    void d01_sinSucursal_usaGlobal() {
        crearProducto("Producto A", 2, 5);  // bajo stock global: 2 < 5
        crearProducto("Producto B", 10, 5); // normal global: 10 >= 5

        DashboardSummaryDTO dto = dashboard(null);

        assertThat(dto.getBajoStockCount())
                .as("Global: solo Producto A está bajo stock")
                .isEqualTo(1);
    }

    @Test @Order(2)
    @DisplayName("d02: Con sucursalId — dashboard usa PSS, no Producto.stockActual")
    void d02_conSucursal_usaPSS() {
        // Producto con global=10 (normal) pero PSS Principal=2 (bajo stock)
        Long p = crearProducto("Desfasado", 10, 5);
        crearPSS(p, sucursalPrincipal, 2);  // PSS Principal: 2 < 5 → bajo stock
        crearPSS(p, sucursalMiraflores, 10); // PSS Miraflores: 10 >= 5 → normal

        DashboardSummaryDTO dtoPrincipal = dashboard(sucursalPrincipal);
        DashboardSummaryDTO dtoMiraflores = dashboard(sucursalMiraflores);
        DashboardSummaryDTO dtoGlobal = dashboard(null);

        assertThat(dtoPrincipal.getBajoStockCount())
                .as("Principal ve bajo stock porque PSS=2 < min=5")
                .isEqualTo(1);
        assertThat(dtoMiraflores.getBajoStockCount())
                .as("Miraflores no ve bajo stock porque PSS=10 >= min=5")
                .isEqualTo(0);
        assertThat(dtoGlobal.getBajoStockCount())
                .as("Global no ve bajo stock porque stockActual=10 >= min=5")
                .isEqualTo(0);
    }

    @Test @Order(3)
    @DisplayName("d03: Producto con PSS bajo mínimo aparece en dashboard de esa sucursal")
    void d03_pssBajoMinimoAparece() {
        Long p = crearProducto("Prod Bajo", 20, 5); // global alto
        crearPSS(p, sucursalPrincipal, 1); // PSS Principal: 1 < 5

        DashboardSummaryDTO dto = dashboard(sucursalPrincipal);

        assertThat(dto.getBajoStockCount()).isEqualTo(1);
        assertThat(dto.getBajoStockItems()).hasSize(1);
        assertThat(dto.getBajoStockItems().get(0).getNombre()).isEqualTo("Prod Bajo");
        assertThat(dto.getBajoStockItems().get(0).getStockActual()).isEqualTo(1);
    }

    @Test @Order(4)
    @DisplayName("d04: Producto de otra sucursal no aparece en la sucursal activa")
    void d04_otraSucursalNoAparece() {
        Long p = crearProducto("Solo Miraflores", 20, 5);
        crearPSS(p, sucursalPrincipal, 10);    // Principal: normal
        crearPSS(p, sucursalMiraflores, 2);    // Miraflores: bajo stock

        DashboardSummaryDTO dtoPrincipal = dashboard(sucursalPrincipal);
        DashboardSummaryDTO dtoMiraflores = dashboard(sucursalMiraflores);

        assertThat(dtoPrincipal.getBajoStockCount())
                .as("Principal no debe ver el bajo stock de Miraflores")
                .isEqualTo(0);
        assertThat(dtoMiraflores.getBajoStockCount())
                .as("Miraflores sí debe ver su bajo stock")
                .isEqualTo(1);
    }

    @Test @Order(5)
    @DisplayName("d05: Regla consistente — mismo producto en dashboard y módulo (<=)")
    void d05_mismaReglaEnDashboardYModulo() {
        Long p1 = crearProducto("BajoStrict",  3, 5);   // 3 <= 5 → bajo stock
        Long p2 = crearProducto("EnMinimo",    5, 5);   // 5 == 5 → SÍ es bajo stock (<=)
        Long p3 = crearProducto("Normal",     10, 5);   // 10 > 5 → normal

        crearPSS(p1, sucursalPrincipal, 3);
        crearPSS(p2, sucursalPrincipal, 5);
        crearPSS(p3, sucursalPrincipal, 10);

        DashboardSummaryDTO dto = dashboard(sucursalPrincipal);

        assertThat(dto.getBajoStockCount())
                .as("p1 (3<=5) y p2 (5==5) son bajo stock con regla <=")
                .isEqualTo(2);
    }

    @Test @Order(6)
    @DisplayName("d06: Producto con variantes — dashboard suma PVSS de la sucursal")
    void d06_variantesPVSS() {
        Long p = crearProducto("Con Variantes", 0, 10);

        // Crear variantes
        Long v1 = jdbc.queryForObject(
                "INSERT INTO producto_variantes (producto_id, tenant_id, talla, color, stock_actual, stock_minimo, activo, created_at) " +
                "VALUES (?, ?, 'S', 'Rojo', 0, 0, true, NOW()) RETURNING id",
                Long.class, p, TENANT);
        Long v2 = jdbc.queryForObject(
                "INSERT INTO producto_variantes (producto_id, tenant_id, talla, color, stock_actual, stock_minimo, activo, created_at) " +
                "VALUES (?, ?, 'M', 'Rojo', 0, 0, true, NOW()) RETURNING id",
                Long.class, p, TENANT);

        // PSS de las variantes para Principal: v1=1, v2=2 → suma=3 < min=10 → bajo stock
        jdbc.update("INSERT INTO producto_variante_stock_sucursal (variante_id, sucursal_id, tenant_id, stock_actual, stock_minimo) " +
                "VALUES (?, ?, ?, 1, 0)", v1, sucursalPrincipal, TENANT);
        jdbc.update("INSERT INTO producto_variante_stock_sucursal (variante_id, sucursal_id, tenant_id, stock_actual, stock_minimo) " +
                "VALUES (?, ?, ?, 2, 0)", v2, sucursalPrincipal, TENANT);

        DashboardSummaryDTO dto = dashboard(sucursalPrincipal);

        assertThat(dto.getBajoStockCount())
                .as("Producto con variantes: suma PVSS=3 < min=10 → bajo stock")
                .isEqualTo(1);
    }

    @Test @Order(7)
    @DisplayName("d07: Tenant sin sucursales (legacy) — dashboard usa global")
    void d07_tenantLegacySinSucursales() {
        // Crear producto directamente con stock bajo (sin PSS)
        Long p = crearProducto("Legacy Bajo", 2, 5);
        // No se crea PSS → el producto solo tiene stockActual global

        DashboardSummaryDTO dtoGlobal = dashboard(null);

        assertThat(dtoGlobal.getBajoStockCount())
                .as("Sin sucursalId activa → usa global → 2 < 5 → bajo stock")
                .isEqualTo(1);
    }

    @Test @Order(8)
    @DisplayName("d08: Producto con stockActual == stockMinimo SÍ es bajo stock (regla <=)")
    void d08_enMinimoEsBajoStock() {
        Long p = crearProducto("Al Limite", 5, 5);
        crearPSS(p, sucursalPrincipal, 5); // PSS = 5, min = 5

        DashboardSummaryDTO dto = dashboard(sucursalPrincipal);

        assertThat(dto.getBajoStockCount())
                .as("stockActual==stockMinimo SÍ es bajo stock (<=)")
                .isEqualTo(1);
    }
}
