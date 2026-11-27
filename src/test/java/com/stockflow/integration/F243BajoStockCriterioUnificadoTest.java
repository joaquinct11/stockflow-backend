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
 * F-24.3 Criterio unificado de stock bajo: stockActual <= stockMinimo.
 * Verifica que Dashboard, PSS y global usan el mismo criterio en todos los módulos.
 *
 * c01 — Stock 0, mínimo 5: debe incluirse (0 <= 5)
 * c02 — Stock 4, mínimo 5: debe incluirse (4 <= 5)
 * c03 — Stock 5, mínimo 5: debe incluirse (5 <= 5) ← el caso que causaba discrepancia
 * c04 — Stock 6, mínimo 5: NO debe incluirse (6 > 5)
 * c05 — Productos con variantes (PVSS)
 * c06 — Productos sin variantes (PSS)
 * c07 — Cambio de sucursal: cada sucursal ve su propio stock
 * c08 — Compatibilidad Botica Legacy Básico: sin sucursalId → usa global
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class F243BajoStockCriterioUnificadoTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    static {
        postgres.start();
    }

    @Autowired private ProductoService productoService;
    @Autowired private JdbcTemplate jdbc;

    private static final String TENANT = "f243-criterio-tenant";

    private Long sucursalPrincipal;
    private Long sucursalSecundaria;
    private Long umId;

    @BeforeAll
    void setup() {
        jdbc.update("INSERT INTO tenants (tenant_id, nombre, activo, rubro) " +
                "VALUES (?, 'F243 Criterio', true, 'BOTICA') ON CONFLICT (tenant_id) DO NOTHING", TENANT);

        sucursalPrincipal = jdbc.queryForObject(
                "INSERT INTO sucursales (nombre, tenant_id, es_principal, activo) " +
                "VALUES ('Principal', ?, true, true) RETURNING id", Long.class, TENANT);

        sucursalSecundaria = jdbc.queryForObject(
                "INSERT INTO sucursales (nombre, tenant_id, es_principal, activo) " +
                "VALUES ('Secundaria', ?, false, true) RETURNING id", Long.class, TENANT);

        umId = jdbc.queryForObject(
                "SELECT id FROM unidad_medida WHERE tenant_id IS NULL LIMIT 1", Long.class);
    }

    @BeforeEach
    void limpiar() {
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
    @DisplayName("c01: Stock 0, mínimo 5 → debe incluirse (0 <= 5)")
    void c01_stockCero_bajoMinimo() {
        Long p = crearProducto("Stock Cero", 0, 5);
        crearPSS(p, sucursalPrincipal, 0);

        DashboardSummaryDTO dto = dashboard(sucursalPrincipal);

        assertThat(dto.getBajoStockCount())
                .as("0 <= 5 → debe incluirse")
                .isEqualTo(1);
    }

    @Test @Order(2)
    @DisplayName("c02: Stock 4, mínimo 5 → debe incluirse (4 <= 5)")
    void c02_stockBajoMinimo() {
        Long p = crearProducto("Stock Bajo", 4, 5);
        crearPSS(p, sucursalPrincipal, 4);

        DashboardSummaryDTO dto = dashboard(sucursalPrincipal);

        assertThat(dto.getBajoStockCount())
                .as("4 <= 5 → debe incluirse")
                .isEqualTo(1);
    }

    @Test @Order(3)
    @DisplayName("c03: Stock 5, mínimo 5 → debe incluirse (5 == 5, regla <=)")
    void c03_stockIgualMinimo() {
        Long p = crearProducto("Stock Exacto", 5, 5);
        crearPSS(p, sucursalPrincipal, 5);

        DashboardSummaryDTO dto = dashboard(sucursalPrincipal);

        assertThat(dto.getBajoStockCount())
                .as("5 <= 5 → debe incluirse (este era el producto con discrepancia entre módulos)")
                .isEqualTo(1);
    }

    @Test @Order(4)
    @DisplayName("c04: Stock 6, mínimo 5 → NO debe incluirse (6 > 5)")
    void c04_stockSobreMinimo() {
        Long p = crearProducto("Stock Normal", 6, 5);
        crearPSS(p, sucursalPrincipal, 6);

        DashboardSummaryDTO dto = dashboard(sucursalPrincipal);

        assertThat(dto.getBajoStockCount())
                .as("6 > 5 → NO debe incluirse")
                .isEqualTo(0);
    }

    @Test @Order(5)
    @DisplayName("c05: Productos con variantes — PVSS usa regla <=")
    void c05_productosConVariantes() {
        Long p = crearProducto("Con Variantes", 0, 10);

        Long v1 = jdbc.queryForObject(
                "INSERT INTO producto_variantes (producto_id, tenant_id, talla, color, stock_actual, stock_minimo, activo, created_at) " +
                "VALUES (?, ?, 'S', 'Azul', 0, 0, true, NOW()) RETURNING id",
                Long.class, p, TENANT);
        Long v2 = jdbc.queryForObject(
                "INSERT INTO producto_variantes (producto_id, tenant_id, talla, color, stock_actual, stock_minimo, activo, created_at) " +
                "VALUES (?, ?, 'M', 'Azul', 0, 0, true, NOW()) RETURNING id",
                Long.class, p, TENANT);

        // Suma PVSS = 5+5=10 == stockMinimo=10 → debe incluirse (<=)
        jdbc.update("INSERT INTO producto_variante_stock_sucursal (variante_id, sucursal_id, tenant_id, stock_actual, stock_minimo) " +
                "VALUES (?, ?, ?, 5, 0)", v1, sucursalPrincipal, TENANT);
        jdbc.update("INSERT INTO producto_variante_stock_sucursal (variante_id, sucursal_id, tenant_id, stock_actual, stock_minimo) " +
                "VALUES (?, ?, ?, 5, 0)", v2, sucursalPrincipal, TENANT);

        DashboardSummaryDTO dto = dashboard(sucursalPrincipal);

        assertThat(dto.getBajoStockCount())
                .as("Variantes: suma PVSS=10 == min=10 → debe incluirse (<=)")
                .isEqualTo(1);
    }

    @Test @Order(6)
    @DisplayName("c06: Productos sin variantes — PSS usa regla <=")
    void c06_productosSinVariantes() {
        Long p = crearProducto("Sin Variantes", 0, 8);
        crearPSS(p, sucursalPrincipal, 8); // PSS = 8 == min = 8

        DashboardSummaryDTO dto = dashboard(sucursalPrincipal);

        assertThat(dto.getBajoStockCount())
                .as("PSS=8 == min=8 → debe incluirse (<=)")
                .isEqualTo(1);
    }

    @Test @Order(7)
    @DisplayName("c07: Cambio de sucursal — cada sucursal ve su propio stock")
    void c07_cambioDeSucursal() {
        Long p = crearProducto("Multi Sucursal", 20, 10);
        crearPSS(p, sucursalPrincipal, 10);    // Principal: 10 == min 10 → incluido
        crearPSS(p, sucursalSecundaria, 15);   // Secundaria: 15 > min 10 → excluido

        DashboardSummaryDTO dtoPrincipal   = dashboard(sucursalPrincipal);
        DashboardSummaryDTO dtoSecundaria  = dashboard(sucursalSecundaria);

        assertThat(dtoPrincipal.getBajoStockCount())
                .as("Principal: PSS=10 == min=10 → incluido (<=)")
                .isEqualTo(1);
        assertThat(dtoSecundaria.getBajoStockCount())
                .as("Secundaria: PSS=15 > min=10 → excluido")
                .isEqualTo(0);
    }

    @Test @Order(8)
    @DisplayName("c08: Botica Legacy Básico — sin sucursalId usa global con regla <=")
    void c08_boticaLegacyCompatibilidad() {
        // Simula el caso Botica Legacy Básico: productos sin PSS, con stockActual global
        crearProducto("Medicamento A", 3, 5);  // 3 <= 5 → incluido
        crearProducto("Medicamento B", 5, 5);  // 5 == 5 → incluido (caso discrepancia)
        crearProducto("Medicamento C", 6, 5);  // 6 > 5 → excluido

        DashboardSummaryDTO dto = dashboard(null); // sin sucursalId → global

        assertThat(dto.getBajoStockCount())
                .as("Botica legacy: A (3<=5) y B (5==5) deben incluirse → 2")
                .isEqualTo(2);
    }
}
