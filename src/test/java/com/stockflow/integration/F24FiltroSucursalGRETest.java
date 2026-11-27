package com.stockflow.integration;

import com.stockflow.dto.gre.GuiaRemisionListResponse;
import com.stockflow.dto.gre.GuiaRemisionStatsResponse;
import com.stockflow.entity.gre.EstadoTributarioGRE;
import com.stockflow.service.GuiaRemisionService;
import com.stockflow.util.TenantContext;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.*;

/**
 * F-24: Filtro de GRE por sucursal activa.
 *
 * f24-01 — Principal muestra GRE con Principal como origen
 * f24-02 — Principal muestra GRE con Principal como destino
 * f24-03 — Principal NO muestra GRE ajenas
 * f24-04 — Miraflores muestra GRE con Miraflores como origen
 * f24-05 — Miraflores muestra GRE con Miraflores como destino
 * f24-06 — Miraflores NO muestra GRE ajenas
 * f24-07 — Transferencia aparece en ambas sucursales
 * f24-08 — Estadísticas respetan la sucursal
 * f24-09 — Búsqueda y paginación respetan la sucursal
 * f24-10 — Aislamiento entre tenants con filtro de sucursal
 * f24-11 — GRE legacy sin sucursal conserva comportamiento previsto
 * f24-12 — Sin sucursalId muestra todas las GRE del tenant
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class F24FiltroSucursalGRETest {

    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    static {
        postgres.start();
    }

    @Autowired private GuiaRemisionService greService;
    @Autowired private JdbcTemplate jdbc;

    private static final String TENANT   = "f24-tenant";
    private static final String TENANT_B = "f24-tenant-b";

    private Long principal;
    private Long miraflores;
    private Long tercera;

    @BeforeAll
    void setup() {
        for (String t : new String[]{TENANT, TENANT_B}) {
            jdbc.update("INSERT INTO tenants (tenant_id, nombre, activo, rubro) " +
                    "VALUES (?, ?, true, 'OTRO') ON CONFLICT (tenant_id) DO NOTHING", t, t);
        }
        principal   = crearSucursal("Principal",   TENANT, true);
        miraflores  = crearSucursal("Miraflores",  TENANT, false);
        tercera     = crearSucursal("Tercera",      TENANT, false);
    }

    @BeforeEach
    void limpiarGREs() {
        // Limpia solo GREs del tenant de prueba para aislar cada test
        jdbc.update("DELETE FROM guia_remision_items WHERE guia_remision_id IN " +
                "(SELECT id FROM guias_remision WHERE tenant_id IN (?, ?))", TENANT, TENANT_B);
        jdbc.update("DELETE FROM guia_remision_eventos WHERE guia_remision_id IN " +
                "(SELECT id FROM guias_remision WHERE tenant_id IN (?, ?))", TENANT, TENANT_B);
        jdbc.update("DELETE FROM guias_remision WHERE tenant_id IN (?, ?)", TENANT, TENANT_B);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Long crearSucursal(String nombre, String tenantId, boolean esPrincipal) {
        return jdbc.queryForObject(
                "INSERT INTO sucursales (nombre, tenant_id, es_principal, activo) " +
                "VALUES (?, ?, ?, true) RETURNING id",
                Long.class, nombre, tenantId, esPrincipal);
    }

    private void crearGRE(Long origenId, Long destinoId, String destinatario, String serie, int correlativo) {
        jdbc.update(
                "INSERT INTO guias_remision " +
                "(tenant_id, serie, correlativo, tipo_traslado, motivo_traslado_codigo, " +
                " modalidad_transporte, fecha_traslado, estado_tributario, estado_logistico, " +
                " sucursal_origen_id, sucursal_destino_id, destinatario_razon_social, " +
                " unidad_medida_peso, idempotency_key) " +
                "VALUES (?, ?, ?, 'SUCURSALES', '04', 'PRIVADO', CURRENT_DATE, " +
                " 'BORRADOR', 'PENDIENTE', ?, ?, ?, 'KGM', gen_random_uuid())",
                TENANT, serie, correlativo, origenId, destinoId, destinatario);
    }

    private Page<GuiaRemisionListResponse> listar(Long sucursalId, String q, EstadoTributarioGRE estado) {
        TenantContext.setCurrentTenant(TENANT);
        try {
            return greService.listar(q, estado, sucursalId,
                    PageRequest.of(0, 25, Sort.by("correlativo").ascending()));
        } finally { TenantContext.clear(); }
    }

    private GuiaRemisionStatsResponse stats(Long sucursalId) {
        TenantContext.setCurrentTenant(TENANT);
        try { return greService.stats(sucursalId); }
        finally { TenantContext.clear(); }
    }

    // ── Tests ────────────────────────────────────────────────────────────────

    @Test @Order(1)
    @DisplayName("f24-01: Principal muestra GRE con Principal como origen")
    void f24_01_principalComoOrigen() {
        crearGRE(principal, miraflores, null, "T001", 1);
        crearGRE(tercera, miraflores, null, "T001", 2); // ajena

        Page<GuiaRemisionListResponse> page = listar(principal, null, null);

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getSucursalOrigenNombre()).isEqualTo("Principal");
    }

    @Test @Order(2)
    @DisplayName("f24-02: Principal muestra GRE con Principal como destino")
    void f24_02_principalComoDestino() {
        crearGRE(miraflores, principal, null, "T001", 1);
        crearGRE(tercera, miraflores, null, "T001", 2); // ajena

        Page<GuiaRemisionListResponse> page = listar(principal, null, null);

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getSucursalDestinoNombre()).isEqualTo("Principal");
    }

    @Test @Order(3)
    @DisplayName("f24-03: Principal NO muestra GRE ajenas")
    void f24_03_principalNoMuestraAjenas() {
        crearGRE(miraflores, tercera, null, "T001", 1); // ajena a Principal
        crearGRE(tercera,    null,    null, "T001", 2); // ajena a Principal (con destino null)

        Page<GuiaRemisionListResponse> page = listar(principal, null, null);

        // Solo legacies (ambas sucursales null) deben aparecer; las ajenas no
        assertThat(page.getContent())
                .as("Principal no debe ver GRE de sucursales ajenas")
                .allMatch(g -> g.getSucursalOrigenNombre() == null && g.getSucursalDestinoNombre() == null
                        || "Principal".equals(g.getSucursalOrigenNombre())
                        || "Principal".equals(g.getSucursalDestinoNombre()));
        assertThat(page.getTotalElements()).isEqualTo(0);
    }

    @Test @Order(4)
    @DisplayName("f24-04: Miraflores muestra GRE con Miraflores como origen")
    void f24_04_mirafloresComoOrigen() {
        crearGRE(miraflores, principal, null, "T001", 1);
        crearGRE(principal, tercera, null, "T001", 2); // ajena

        Page<GuiaRemisionListResponse> page = listar(miraflores, null, null);

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getSucursalOrigenNombre()).isEqualTo("Miraflores");
    }

    @Test @Order(5)
    @DisplayName("f24-05: Miraflores muestra GRE con Miraflores como destino")
    void f24_05_mirafloresComoDestino() {
        crearGRE(principal, miraflores, null, "T001", 1);
        crearGRE(principal, tercera,    null, "T001", 2); // ajena

        Page<GuiaRemisionListResponse> page = listar(miraflores, null, null);

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getSucursalDestinoNombre()).isEqualTo("Miraflores");
    }

    @Test @Order(6)
    @DisplayName("f24-06: Miraflores NO muestra GRE ajenas")
    void f24_06_mirafloresNoMuestraAjenas() {
        crearGRE(principal, tercera, null, "T001", 1); // ajena a Miraflores

        Page<GuiaRemisionListResponse> page = listar(miraflores, null, null);

        assertThat(page.getTotalElements()).isEqualTo(0);
    }

    @Test @Order(7)
    @DisplayName("f24-07: Transferencia aparece en ambas sucursales")
    void f24_07_transferenciaEnAmbas() {
        crearGRE(principal, miraflores, null, "T001", 1);

        Page<GuiaRemisionListResponse> desdePrincipal  = listar(principal,  null, null);
        Page<GuiaRemisionListResponse> desdeMiraflores = listar(miraflores, null, null);

        assertThat(desdePrincipal.getTotalElements())
                .as("Principal debe ver la transferencia").isEqualTo(1);
        assertThat(desdeMiraflores.getTotalElements())
                .as("Miraflores debe ver la transferencia").isEqualTo(1);
        assertThat(desdePrincipal.getContent().get(0).getId())
                .isEqualTo(desdeMiraflores.getContent().get(0).getId());
    }

    @Test @Order(8)
    @DisplayName("f24-08: Estadísticas respetan la sucursal")
    void f24_08_estadisticasRespetanSucursal() {
        crearGRE(principal, miraflores, null, "T001", 1); // visible en ambas
        crearGRE(miraflores, tercera,   null, "T001", 2); // solo Miraflores y Tercera
        crearGRE(tercera,    null,       null, "T001", 3); // solo Tercera

        GuiaRemisionStatsResponse spPrincipal   = stats(principal);
        GuiaRemisionStatsResponse spMiraflores  = stats(miraflores);
        GuiaRemisionStatsResponse spTercera     = stats(tercera);
        GuiaRemisionStatsResponse spGlobal      = stats(null);

        assertThat(spPrincipal.getTotal()).isEqualTo(1);   // solo GRE #1
        assertThat(spMiraflores.getTotal()).isEqualTo(2);  // GREs #1 y #2
        assertThat(spTercera.getTotal()).isEqualTo(2);     // GREs #2 y #3
        assertThat(spGlobal.getTotal()).isEqualTo(3);      // todas
    }

    @Test @Order(9)
    @DisplayName("f24-09: Búsqueda y paginación respetan la sucursal")
    void f24_09_busquedaYPaginacionRespetanSucursal() {
        crearGRE(principal, miraflores, "Cliente ABC", "T001", 1);
        crearGRE(principal, miraflores, "Cliente XYZ", "T001", 2);
        crearGRE(tercera,   null,       "Cliente ABC", "T001", 3); // ajena a Principal

        // Búsqueda en Principal: solo debe ver las 2 de Principal
        TenantContext.setCurrentTenant(TENANT);
        try {
            Page<GuiaRemisionListResponse> all = greService.listar(null, null, principal,
                    PageRequest.of(0, 25, Sort.by("correlativo").ascending()));
            assertThat(all.getTotalElements()).isEqualTo(2);

            // Búsqueda por "ABC" en Principal: solo la de Principal
            Page<GuiaRemisionListResponse> search = greService.listar("ABC", null, principal,
                    PageRequest.of(0, 25, Sort.by("correlativo").ascending()));
            assertThat(search.getTotalElements()).isEqualTo(1);
            assertThat(search.getContent().get(0).getDestinatarioRazonSocial()).isEqualTo("Cliente ABC");

            // Paginación: página 0 con size=1
            Page<GuiaRemisionListResponse> p0 = greService.listar(null, null, principal,
                    PageRequest.of(0, 1, Sort.by("correlativo").ascending()));
            Page<GuiaRemisionListResponse> p1 = greService.listar(null, null, principal,
                    PageRequest.of(1, 1, Sort.by("correlativo").ascending()));
            assertThat(p0.getTotalElements()).isEqualTo(2);
            assertThat(p0.getContent()).hasSize(1);
            assertThat(p1.getContent()).hasSize(1);
            assertThat(p0.getContent().get(0).getId()).isNotEqualTo(p1.getContent().get(0).getId());
        } finally { TenantContext.clear(); }
    }

    @Test @Order(10)
    @DisplayName("f24-10: Aislamiento entre tenants con filtro de sucursal")
    void f24_10_aislamientoEntreTenantsConSucursal() {
        crearGRE(principal, miraflores, null, "T001", 1);

        Long sucB = crearSucursal("Principal B", TENANT_B, true);
        jdbc.update(
                "INSERT INTO guias_remision " +
                "(tenant_id, serie, correlativo, tipo_traslado, motivo_traslado_codigo, " +
                " modalidad_transporte, fecha_traslado, estado_tributario, estado_logistico, " +
                " sucursal_origen_id, unidad_medida_peso, idempotency_key) " +
                "VALUES (?, 'T001', 1, 'SUCURSALES', '04', 'PRIVADO', CURRENT_DATE, " +
                "'BORRADOR', 'PENDIENTE', ?, 'KGM', gen_random_uuid())",
                TENANT_B, sucB);

        // Tenant A filtrando por su sucursal Principal: solo ve la suya
        TenantContext.setCurrentTenant(TENANT);
        try {
            Page<GuiaRemisionListResponse> pageA = greService.listar(null, null, principal,
                    PageRequest.of(0, 25, Sort.by("createdAt").descending()));
            assertThat(pageA.getTotalElements()).isEqualTo(1);
        } finally { TenantContext.clear(); }

        // Tenant B filtrando por su sucursal: solo ve la suya
        TenantContext.setCurrentTenant(TENANT_B);
        try {
            Page<GuiaRemisionListResponse> pageB = greService.listar(null, null, sucB,
                    PageRequest.of(0, 25, Sort.by("createdAt").descending()));
            assertThat(pageB.getTotalElements()).isEqualTo(1);
        } finally { TenantContext.clear(); }
    }

    @Test @Order(11)
    @DisplayName("f24-11: GRE sin sucursal solo aparece en vista global, no en vistas filtradas")
    void f24_11_sinSucursalSoloEnVistaGlobal() {
        // GRE sin sucursal (legacy o delivery sin contexto de sucursal)
        jdbc.update(
                "INSERT INTO guias_remision " +
                "(tenant_id, serie, correlativo, tipo_traslado, motivo_traslado_codigo, " +
                " modalidad_transporte, fecha_traslado, estado_tributario, estado_logistico, " +
                " sucursal_origen_id, sucursal_destino_id, unidad_medida_peso, idempotency_key) " +
                "VALUES (?, 'T001', 1, 'DELIVERY', '01', 'PUBLICO', CURRENT_DATE, " +
                "'BORRADOR', 'PENDIENTE', NULL, NULL, 'KGM', gen_random_uuid())",
                TENANT);
        crearGRE(principal, miraflores, null, "T001", 2); // con sucursal

        // Sin filtro de sucursal: se ven las dos (compatibilidad backward)
        Page<GuiaRemisionListResponse> todas = listar(null, null, null);
        assertThat(todas.getTotalElements()).isEqualTo(2);

        // Con filtro de sucursal Principal: solo la GRE con origen=Principal
        // La GRE sin sucursal no tiene relación con Principal → no debe aparecer
        Page<GuiaRemisionListResponse> conFiltro = listar(principal, null, null);
        assertThat(conFiltro.getTotalElements()).isEqualTo(1);
        assertThat(conFiltro.getContent().get(0).getSucursalOrigenNombre()).isEqualTo("Principal");

        // Con filtro de sucursal Miraflores: solo la GRE con destino=Miraflores
        Page<GuiaRemisionListResponse> conMiraflores = listar(miraflores, null, null);
        assertThat(conMiraflores.getTotalElements()).isEqualTo(1);
        assertThat(conMiraflores.getContent().get(0).getSucursalDestinoNombre()).isEqualTo("Miraflores");
    }

    @Test @Order(12)
    @DisplayName("f24-12: Sin sucursalId muestra todas las GRE del tenant (compatibilidad hacia atrás)")
    void f24_12_sinSucursalIdMuestraTodasLasGRE() {
        crearGRE(principal,  miraflores, null, "T001", 1);
        crearGRE(miraflores, tercera,    null, "T001", 2);
        crearGRE(tercera,    principal,  null, "T001", 3);

        Page<GuiaRemisionListResponse> page = listar(null, null, null);

        assertThat(page.getTotalElements()).isEqualTo(3);
    }
}
