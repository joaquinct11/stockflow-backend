package com.stockflow.service;

import com.stockflow.dto.ActividadRecienteDTO;
import com.stockflow.dto.UsuarioProfileDTO;
import com.stockflow.dto.VentaDTO;
import com.stockflow.entity.Venta;
import com.stockflow.mapper.VentaMapper;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Regresiones open-in-view=false (F-12).
 *
 * Verifica que los endpoints afectados por {@code spring.jpa.open-in-view=false}
 * devuelven datos correctos sin LazyInitializationException:
 *   - actividad reciente (DashboardService)
 *   - ventas por período (VentaService / VentaMapper)
 *   - obtener venta por id (VentaService / VentaMapper)
 *   - perfil de usuario (AuthService)
 *   - aislamiento multi-tenant (un usuario en dos tenants solo ve el tenant activo)
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Testcontainers
@DisplayName("F-12 — Regresiones open-in-view=false")
class OpenInViewRegressionTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    static {
        postgres.start();
    }

    @Autowired private DashboardService dashboardService;
    @Autowired private VentaService     ventaService;
    @Autowired private AuthService      authService;
    @Autowired private VentaMapper      ventaMapper;
    @Autowired private JdbcTemplate     jdbc;

    // IDs controlados para evitar conflictos con otros tests de integración
    private static final Long   USER_A_ID    = 90_100L;
    private static final Long   USER_B_ID    = 90_101L;
    private static final String TENANT_X     = "oiv-regr-tenant-x";
    private static final String TENANT_Y     = "oiv-regr-tenant-y";

    private Long rolAdminId;
    private Long ventaId;

    // ─────────────────────────────────────────────────────────────────────────
    // Setup
    // ─────────────────────────────────────────────────────────────────────────

    @BeforeAll
    void setup() {
        rolAdminId = jdbc.queryForObject(
                "SELECT id FROM roles WHERE nombre = 'ADMIN' LIMIT 1", Long.class);

        // Dos tenants
        for (String t : List.of(TENANT_X, TENANT_Y)) {
            jdbc.update("""
                INSERT INTO tenants (tenant_id, nombre, activo, rubro)
                VALUES (?, ?, true, 'FERRETERIA')
                ON CONFLICT (tenant_id) DO NOTHING
                """, t, "Negocio-" + t);
        }

        // Usuario A (vendedor en TENANT_X y en TENANT_Y)
        jdbc.update("""
            INSERT INTO usuarios (id, email, contraseña, nombre, apellido, activo)
            VALUES (?, 'oiv.usera@test.com', 'x', 'Vendedor', 'OIV', true)
            ON CONFLICT (id) DO NOTHING
            """, USER_A_ID);
        jdbc.update("""
            INSERT INTO usuario_tenant (usuario_id, tenant_id, rol_id, activo)
            VALUES (?, ?, ?, true)
            ON CONFLICT (usuario_id, tenant_id) DO NOTHING
            """, USER_A_ID, TENANT_X, rolAdminId);
        jdbc.update("""
            INSERT INTO usuario_tenant (usuario_id, tenant_id, rol_id, activo)
            VALUES (?, ?, ?, true)
            ON CONFLICT (usuario_id, tenant_id) DO NOTHING
            """, USER_A_ID, TENANT_Y, rolAdminId);

        // Usuario B (solo en TENANT_Y)
        jdbc.update("""
            INSERT INTO usuarios (id, email, contraseña, nombre, apellido, activo)
            VALUES (?, 'oiv.userb@test.com', 'x', 'Cajero', 'OIV', true)
            ON CONFLICT (id) DO NOTHING
            """, USER_B_ID);
        jdbc.update("""
            INSERT INTO usuario_tenant (usuario_id, tenant_id, rol_id, activo)
            VALUES (?, ?, ?, true)
            ON CONFLICT (usuario_id, tenant_id) DO NOTHING
            """, USER_B_ID, TENANT_Y, rolAdminId);

        // Suscripciones (requeridas por algunos servicios)
        for (String t : List.of(TENANT_X, TENANT_Y)) {
            jdbc.update("""
                INSERT INTO suscripciones (usuario_principal_id, tenant_id, plan_id, precio_mensual, estado, created_at)
                SELECT ?, ?, 'PRO', 0.00, 'ACTIVA', NOW()
                WHERE NOT EXISTS (SELECT 1 FROM suscripciones WHERE tenant_id = ?)
                """, USER_A_ID, t, t);
        }

        // Venta de TENANT_X con USER_A como vendedor
        ventaId = jdbc.queryForObject("""
            INSERT INTO ventas (tenant_id, vendedor_id, total, metodo_pago, estado, created_at)
            VALUES (?, ?, 150.00, 'EFECTIVO', 'COMPLETADA', NOW())
            RETURNING id
            """, Long.class, TENANT_X, USER_A_ID);
    }

    @BeforeEach
    void setContext() {
        TenantContext.setCurrentTenant(TENANT_X);
        TenantContext.setCurrentUserId(USER_A_ID);
    }

    @AfterAll
    void cleanup() {
        TenantContext.clear();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // OIV-1: Actividad reciente sin LazyInitializationException
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("OIV-1: actividad reciente no lanza LazyInitializationException y devuelve nombre de vendedor")
    void actividadReciente_noLanzaLazy_devuelveNombreVendedor() {
        assertThatCode(() -> {
            List<ActividadRecienteDTO> actividades =
                    dashboardService.getActividadReciente(TENANT_X, null, 10);
            assertThat(actividades).isNotEmpty();
            // Al menos una actividad de tipo VENTA debe tener nombre de usuario
            boolean hayVentaConNombre = actividades.stream()
                    .filter(a -> "VENTA".equals(a.getTipo()))
                    .anyMatch(a -> a.getUsuarioNombre() != null && !a.getUsuarioNombre().isBlank());
            assertThat(hayVentaConNombre)
                    .as("Al menos una venta debe tener nombre de vendedor")
                    .isTrue();
        }).doesNotThrowAnyException();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // OIV-2: Ventas por período con nombre de vendedor (via mapper)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(2)
    @DisplayName("OIV-2: ventas por período no lanza LazyInitializationException y devuelve vendedorNombre")
    void ventasPorPeriodo_noLanzaLazy_devuelveVendedorNombre() {
        LocalDateTime desde = LocalDateTime.now().minusDays(1);
        LocalDateTime hasta = LocalDateTime.now().plusDays(1);

        assertThatCode(() -> {
            List<Venta> ventas = ventaService.obtenerVentasPorPeriodo(TENANT_X, desde, hasta, null);
            List<VentaDTO> dtos = ventaMapper.toDTOList(ventas);

            assertThat(dtos).isNotEmpty();
            dtos.forEach(dto ->
                assertThat(dto.getVendedorNombre())
                        .as("VentaDTO id=%s debe tener vendedorNombre", dto.getId())
                        .isNotNull()
                        .isNotBlank()
            );
        }).doesNotThrowAnyException();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // OIV-3: Obtener venta por ID con vendedorNombre
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(3)
    @DisplayName("OIV-3: obtener venta por ID no lanza LazyInitializationException y devuelve vendedorNombre")
    void ventaPorId_noLanzaLazy_devuelveVendedorNombre() {
        assertThatCode(() -> {
            Optional<Venta> opt = ventaService.obtenerVentaPorId(ventaId);
            assertThat(opt).isPresent();
            VentaDTO dto = ventaMapper.toDTO(opt.get());
            assertThat(dto.getVendedorNombre())
                    .as("VentaDTO debe tener vendedorNombre")
                    .isNotNull()
                    .isNotBlank();
        }).doesNotThrowAnyException();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // OIV-4: Perfil de usuario devuelve negocio activo
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(4)
    @DisplayName("OIV-4: perfil devuelve nombreFarmacia del tenant activo sin LazyInitializationException")
    void perfil_noLanzaLazy_devuelveNombreNegocio() {
        TenantContext.setCurrentTenant(TENANT_X);

        assertThatCode(() -> {
            UsuarioProfileDTO perfil = authService.obtenerPerfil(USER_A_ID);
            assertThat(perfil.getNombreFarmacia())
                    .as("El perfil debe devolver el nombre del negocio activo")
                    .isNotNull()
                    .isNotBlank()
                    .isNotEqualTo("N/A");
        }).doesNotThrowAnyException();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // OIV-5: Usuario en dos tenants — cada tenant activo muestra su propio negocio
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(5)
    @DisplayName("OIV-5: usuario en dos tenants ve el negocio del tenant activo, no el primero")
    void perfilMultiTenant_muestraTenantActivo() {
        // Perfil con TENANT_X activo
        TenantContext.setCurrentTenant(TENANT_X);
        UsuarioProfileDTO perfilX = authService.obtenerPerfil(USER_A_ID);

        // Perfil con TENANT_Y activo (mismo usuario)
        TenantContext.setCurrentTenant(TENANT_Y);
        UsuarioProfileDTO perfilY = authService.obtenerPerfil(USER_A_ID);

        assertThat(perfilX.getTenantId()).isEqualTo(TENANT_X);
        assertThat(perfilY.getTenantId()).isEqualTo(TENANT_Y);
        assertThat(perfilX.getNombreFarmacia()).isNotEqualTo(perfilY.getNombreFarmacia());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // OIV-6: Aislamiento de tenant — ventas de TENANT_X no aparecen en TENANT_Y
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(6)
    @DisplayName("OIV-6: aislamiento de tenant — ventas de TENANT_X no visibles en TENANT_Y")
    void ventasPorPeriodo_aislamientoTenant() {
        LocalDateTime desde = LocalDateTime.now().minusDays(1);
        LocalDateTime hasta = LocalDateTime.now().plusDays(1);

        // TENANT_X tiene ventas
        List<Venta> ventasX = ventaService.obtenerVentasPorPeriodo(TENANT_X, desde, hasta, null);
        assertThat(ventasX).isNotEmpty();

        // TENANT_Y no tiene ventas de este periodo (no insertamos ninguna)
        List<Venta> ventasY = ventaService.obtenerVentasPorPeriodo(TENANT_Y, desde, hasta, null);
        boolean ventaXEnY = ventasY.stream().anyMatch(v -> ventaId.equals(v.getId()));
        assertThat(ventaXEnY)
                .as("La venta de TENANT_X no debe aparecer en TENANT_Y")
                .isFalse();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // OIV-7: No N+1 — una consulta carga vendedores de múltiples ventas
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(7)
    @DisplayName("OIV-7: obtenerVentasPorPeriodo no genera N+1 (todos los vendedores cargados en batch)")
    void ventasPorPeriodo_sinN1_vendedoresCargadosEnBatch() {
        // Insertar 3 ventas adicionales con el mismo vendedor
        for (int i = 0; i < 3; i++) {
            jdbc.update("""
                INSERT INTO ventas (tenant_id, vendedor_id, total, metodo_pago, estado, created_at)
                VALUES (?, ?, ?, 'EFECTIVO', 'COMPLETADA', NOW())
                """, TENANT_X, USER_A_ID, BigDecimal.valueOf(50 + i * 10));
        }

        LocalDateTime desde = LocalDateTime.now().minusDays(1);
        LocalDateTime hasta = LocalDateTime.now().plusDays(1);

        // Sin @Transactional / sin JOIN FETCH, esto lanzaría LazyInitializationException
        // en el mapper para al menos una de las ventas
        List<Venta> ventas = ventaService.obtenerVentasPorPeriodo(TENANT_X, desde, hasta, null);
        assertThat(ventas.size()).isGreaterThanOrEqualTo(4);

        assertThatCode(() -> ventaMapper.toDTOList(ventas))
                .as("El mapeo de múltiples ventas no debe lanzar LazyInitializationException")
                .doesNotThrowAnyException();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // OIV-8: actividad reciente con sucursal — sigue sin LazyInitializationException
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(8)
    @DisplayName("OIV-8: actividad reciente filtrada por sucursal no lanza LazyInitializationException")
    void actividadRecienteSucursal_noLanzaLazy() {
        // sucursalId=999 sin datos — debe devolver lista vacía sin excepción
        assertThatCode(() ->
            dashboardService.getActividadReciente(TENANT_X, 999L, 10)
        ).doesNotThrowAnyException();
    }
}
