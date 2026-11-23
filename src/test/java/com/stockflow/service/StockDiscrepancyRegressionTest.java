package com.stockflow.service;

import com.stockflow.dto.DashboardSummaryDTO;
import com.stockflow.dto.UsuarioDTO;
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

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Regresiones F-12 — Problema 1 (stock bajo 19 vs 18) y Problema 2 (AdminController 500).
 *
 * <p>Problema 1: {@code findProductosBajoStockParaScheduler} no filtraba {@code activo=true},
 * causando que el Dashboard contara productos inactivos como "bajo stock", mientras que la
 * pantalla Productos solo muestra activos → diferencia de 1.</p>
 *
 * <p>Problema 2: {@code AdminController.listarUsuarios()} usaba {@code findByTenantIdAndActivoTrue}
 * (sin JOIN FETCH) → acceso a {@code ut.getUsuario()} fuera de sesión → LazyInitializationException.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Testcontainers
@DisplayName("F-12 — Stock discrepancy + AdminController lazy")
class StockDiscrepancyRegressionTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    static {
        postgres.start();
    }

    @Autowired private ProductoService productoService;
    @Autowired private com.stockflow.repository.UsuarioTenantRepository usuarioTenantRepository;
    @Autowired private com.stockflow.mapper.UsuarioMapper usuarioMapper;
    @Autowired private JdbcTemplate jdbc;

    private static final String TENANT   = "stock-regr-tenant-01";
    private static final Long   USER_ID  = 91_000L;
    private Long rolAdminId;
    private Long unidadMedidaId;

    // Producto IDs reservados (altos para no colisionar)
    private static final Long P_ACTIVO_BAJO    = 91_001L; // activo=true,  stock < minimo
    private static final Long P_ACTIVO_OK      = 91_002L; // activo=true,  stock >= minimo
    private static final Long P_INACTIVO_BAJO  = 91_003L; // activo=false, stock < minimo ← el 19.o falso
    private static final Long P_SIN_MINIMO     = 91_004L; // activo=true,  stockMinimo = 0

    // ─────────────────────────────────────────────────────────────────────────
    // Setup
    // ─────────────────────────────────────────────────────────────────────────

    @BeforeAll
    void setup() {
        rolAdminId = jdbc.queryForObject(
                "SELECT id FROM roles WHERE nombre = 'ADMIN' LIMIT 1", Long.class);
        unidadMedidaId = jdbc.queryForObject(
                "SELECT id FROM unidad_medida WHERE tenant_id IS NULL LIMIT 1", Long.class);

        jdbc.update("""
            INSERT INTO tenants (tenant_id, nombre, activo, rubro)
            VALUES (?, 'Bodega Regr Test', true, 'FERRETERIA')
            ON CONFLICT (tenant_id) DO NOTHING
            """, TENANT);

        jdbc.update("""
            INSERT INTO usuarios (id, email, contraseña, nombre, apellido, activo)
            VALUES (?, 'stock.regr@test.com', 'x', 'Admin', 'Regr', true)
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

        // Producto activo con stock bajo (debe aparecer en ambos contadores)
        insertProducto(P_ACTIVO_BAJO, "Paracetamol Regr", TENANT, true,  2, 10);
        // Producto activo con stock suficiente
        insertProducto(P_ACTIVO_OK,   "Ibuprofeno Regr",  TENANT, true,  15, 10);
        // Producto INACTIVO con stock bajo (causa la discrepancia: dashboard lo contaba, Productos no)
        insertProducto(P_INACTIVO_BAJO, "Aspirina Inactiva Regr", TENANT, false, 1, 10);
        // Producto activo sin mínimo definido (stockMinimo=0, no debería contar como bajo stock)
        insertProducto(P_SIN_MINIMO, "Vitamina C Regr", TENANT, true, 5, 0);
    }

    private void insertProducto(Long id, String nombre, String tenantId,
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
    // SD-1: Dashboard solo cuenta productos ACTIVOS con stock bajo
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("SD-1: dashboard-summary bajoStockCount no incluye productos inactivos")
    void dashboardSummary_noContaProductosInactivos() {
        DashboardSummaryDTO summary = productoService.getDashboardSummary(TENANT, null);

        // P_ACTIVO_BAJO debe contarse; P_INACTIVO_BAJO NO debe contarse
        boolean incluyeInactivo = summary.getBajoStockItems().stream()
                .anyMatch(item -> P_INACTIVO_BAJO.equals(item.getId()));
        assertThat(incluyeInactivo)
                .as("El producto inactivo P_INACTIVO_BAJO no debe aparecer en el dashboard")
                .isFalse();

        // El activo con stock bajo sí debe aparecer
        boolean incluyeActivo = summary.getBajoStockItems().stream()
                .anyMatch(item -> P_ACTIVO_BAJO.equals(item.getId()));
        assertThat(incluyeActivo)
                .as("El producto activo P_ACTIVO_BAJO debe aparecer en el dashboard")
                .isTrue();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SD-2: findProductosBajoStock también filtra activo=true
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(2)
    @DisplayName("SD-2: obtenerProductosBajoStock no incluye productos inactivos")
    void obtenerProductosBajoStock_noContaInactivos() {
        List<com.stockflow.entity.Producto> bajoStock =
                productoService.obtenerProductosBajoStock(TENANT);

        boolean incluyeInactivo = bajoStock.stream()
                .anyMatch(p -> P_INACTIVO_BAJO.equals(p.getId()));
        assertThat(incluyeInactivo)
                .as("findProductosBajoStock no debe devolver el producto inactivo")
                .isFalse();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SD-3: Producto con stockMinimo=0 no se contabiliza como "bajo stock"
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(3)
    @DisplayName("SD-3: producto activo con stockMinimo=0 no aparece como bajo stock")
    void productoSinMinimo_noEsBajoStock() {
        DashboardSummaryDTO summary = productoService.getDashboardSummary(TENANT, null);

        boolean incluyeSinMinimo = summary.getBajoStockItems().stream()
                .anyMatch(item -> P_SIN_MINIMO.equals(item.getId()));
        assertThat(incluyeSinMinimo)
                .as("Producto con stockMinimo=0 no debe contarse como bajo stock")
                .isFalse();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SD-4: Dashboard y Productos usan el mismo criterio (mismo conjunto)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(4)
    @DisplayName("SD-4: dashboard bajoStockCount coincide con count de obtenerProductosBajoStock")
    void dashboardYProductosCuentanIgual() {
        DashboardSummaryDTO summary = productoService.getDashboardSummary(TENANT, null);
        List<com.stockflow.entity.Producto> lista = productoService.obtenerProductosBajoStock(TENANT);

        assertThat(summary.getBajoStockCount())
                .as("bajoStockCount del dashboard debe coincidir con la lista de Productos")
                .isEqualTo(lista.size());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SD-5: totalProductos solo cuenta activos
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(5)
    @DisplayName("SD-5: totalProductos en dashboard-summary solo cuenta activos")
    void totalProductos_soloActivos() {
        DashboardSummaryDTO summary = productoService.getDashboardSummary(TENANT, null);

        // Tenemos 3 activos (P_ACTIVO_BAJO, P_ACTIVO_OK, P_SIN_MINIMO) y 1 inactivo
        // La suma puede incluir otros productos de otros tests, pero el inactivo no debe sumarse
        // Verificamos que el total es consistente con countByTenantIdAndActivoTrue
        long countDb = jdbc.queryForObject(
                "SELECT COUNT(*) FROM productos WHERE tenant_id = ? AND activo = true",
                Long.class, TENANT);
        assertThat((long) summary.getTotalProductos()).isEqualTo(countDb);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SD-6: AdminController.listarUsuarios sin LazyInitializationException (Problema 2)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(6)
    @DisplayName("SD-6: listar usuarios del tenant no lanza LazyInitializationException")
    void listarUsuariosAdmin_noLanzaLazy() {
        assertThatCode(() -> {
            List<UsuarioDTO> dtos = usuarioTenantRepository
                    .findByTenantIdActivoFetch(TENANT)
                    .stream()
                    .map(ut -> {
                        UsuarioDTO dto = usuarioMapper.toDTO(ut.getUsuario());
                        dto.setRolNombre(ut.getRol().getNombre());
                        dto.setSucursalId(ut.getSucursalId());
                        dto.setTenantId(TENANT);
                        return dto;
                    })
                    .toList();

            assertThat(dtos).isNotEmpty();
            dtos.forEach(dto -> {
                assertThat(dto.getNombre()).isNotNull().isNotBlank();
                assertThat(dto.getRolNombre()).isNotNull().isNotBlank();
            });
        }).doesNotThrowAnyException();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SD-7: Aislamiento de tenant — usuarios de otro tenant no aparecen
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(7)
    @DisplayName("SD-7: listar usuarios no expone usuarios de otro tenant")
    void listarUsuariosAdmin_aislamientoTenant() {
        List<com.stockflow.entity.UsuarioTenant> del_tenant =
                usuarioTenantRepository.findByTenantIdActivoFetch(TENANT);

        // Todos los registros devueltos deben pertenecer al TENANT correcto
        del_tenant.forEach(ut ->
            assertThat(ut.getTenantId())
                    .as("Todos los usuarios deben pertenecer al tenant correcto")
                    .isEqualTo(TENANT)
        );
    }
}
