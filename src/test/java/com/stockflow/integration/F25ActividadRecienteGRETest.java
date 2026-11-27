package com.stockflow.integration;

import com.stockflow.dto.ActividadRecienteDTO;
import com.stockflow.service.DashboardService;
import com.stockflow.util.TenantContext;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * F-25 Actividad reciente Dashboard — integración de eventos GRE.
 *
 * a01 — Sin eventos GRE: actividad no contiene ningún tipo GRE_*
 * a02 — Evento CREADA → tipo GRE_CREADA con descripción "creada (borrador)"
 * a03 — Evento EMITIDA → tipo GRE_EMITIDA con descripción "emitida exitosamente"
 * a04 — Evento RECEPCION_CONFIRMADA → tipo GRE_RECEPCION_CONFIRMADA
 * a05 — Evento CANCELADA → tipo GRE_CANCELADA
 * a06 — Evento ERROR_LUCODE → tipo GRE_ERROR_LUCODE
 * a07 — Evento EMISION_INICIADA → NO aparece (no está en TIPOS_GRE_ACTIVIDAD)
 * a08 — Filtro sucursal: GRE con origen = sucursalId → incluida
 * a09 — Filtro sucursal: GRE con destino = sucursalId → incluida (OR)
 * a10 — Filtro sucursal: GRE de otra sucursal → NO incluida
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class F25ActividadRecienteGRETest {

    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    static {
        postgres.start();
    }

    @Autowired private DashboardService dashboardService;
    @Autowired private JdbcTemplate jdbc;

    private static final String TENANT = "f25-actividad-gre-tenant";

    private Long sucursalOrigen;
    private Long sucursalDestino;
    private Long sucursalOtra;

    @BeforeAll
    void setup() {
        jdbc.update("INSERT INTO tenants (tenant_id, nombre, activo, rubro) " +
                "VALUES (?, 'F25 GRE Actividad', true, 'BOTICA') ON CONFLICT (tenant_id) DO NOTHING", TENANT);

        sucursalOrigen = jdbc.queryForObject(
                "INSERT INTO sucursales (nombre, tenant_id, es_principal, activo) " +
                "VALUES ('Origen', ?, true, true) RETURNING id", Long.class, TENANT);

        sucursalDestino = jdbc.queryForObject(
                "INSERT INTO sucursales (nombre, tenant_id, es_principal, activo) " +
                "VALUES ('Destino', ?, false, true) RETURNING id", Long.class, TENANT);

        sucursalOtra = jdbc.queryForObject(
                "INSERT INTO sucursales (nombre, tenant_id, es_principal, activo) " +
                "VALUES ('Otra', ?, false, true) RETURNING id", Long.class, TENANT);
    }

    @BeforeEach
    void limpiar() {
        jdbc.update("DELETE FROM guia_remision_eventos WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM guia_remision_items WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM guias_remision WHERE tenant_id = ?", TENANT);
    }

    private Long crearGRE(Long origenId, Long destinoId, String serie, int correlativo) {
        return jdbc.queryForObject(
                "INSERT INTO guias_remision " +
                "(tenant_id, serie, correlativo, tipo_traslado, motivo_traslado_codigo, " +
                " modalidad_transporte, fecha_traslado, estado_tributario, estado_logistico, " +
                " sucursal_origen_id, sucursal_destino_id, destinatario_razon_social, " +
                " unidad_medida_peso, idempotency_key) " +
                "VALUES (?, ?, ?, 'SUCURSALES', '04', 'PRIVADO', CURRENT_DATE, " +
                " 'BORRADOR', 'PENDIENTE', ?, ?, 'Cliente Test', 'KGM', gen_random_uuid()) RETURNING id",
                Long.class, TENANT, serie, correlativo, origenId, destinoId);
    }

    private void crearEvento(Long greId, String tipoEvento, String descripcion) {
        jdbc.update("INSERT INTO guia_remision_eventos (guia_remision_id, tenant_id, tipo_evento, descripcion) " +
                "VALUES (?, ?, ?, ?)", greId, TENANT, tipoEvento, descripcion);
    }

    private List<ActividadRecienteDTO> actividad(Long sucursalId) {
        TenantContext.setCurrentTenant(TENANT);
        try { return dashboardService.getActividadReciente(TENANT, sucursalId, 25); }
        finally { TenantContext.clear(); }
    }

    // ── Tests ────────────────────────────────────────────────────────────────

    @Test @Order(1)
    @DisplayName("a01: Sin eventos GRE → actividad no contiene tipo GRE_*")
    void a01_sinEventosGRE_noAparecenEnActividad() {
        // No hay GREs ni eventos
        List<ActividadRecienteDTO> result = actividad(null);

        assertThat(result)
                .as("No debe haber actividades GRE cuando no existen eventos")
                .noneMatch(a -> a.getTipo().startsWith("GRE_"));
    }

    @Test @Order(2)
    @DisplayName("a02: Evento CREADA → tipo GRE_CREADA con descripción de borrador")
    void a02_eventoCreada_apareceComo_GRE_CREADA() {
        Long greId = crearGRE(sucursalOrigen, sucursalDestino, "T001", 1);
        crearEvento(greId, "CREADA", "GRE creada desde venta");

        List<ActividadRecienteDTO> result = actividad(null);

        assertThat(result)
                .as("Debe aparecer exactamente una actividad GRE_CREADA")
                .filteredOn(a -> "GRE_CREADA".equals(a.getTipo()))
                .hasSize(1)
                .first()
                .satisfies(a -> {
                    assertThat(a.getDescripcion()).as("descripción debe mencionar el borrador").contains("T001-1");
                    assertThat(a.getDescripcion()).contains("borrador");
                });
    }

    @Test @Order(3)
    @DisplayName("a03: Evento EMITIDA → tipo GRE_EMITIDA con descripción de emisión exitosa")
    void a03_eventoEmitida_apareceComo_GRE_EMITIDA() {
        Long greId = crearGRE(sucursalOrigen, sucursalDestino, "T001", 2);
        crearEvento(greId, "EMITIDA", "Emitida por SUNAT");

        List<ActividadRecienteDTO> result = actividad(null);

        assertThat(result)
                .filteredOn(a -> "GRE_EMITIDA".equals(a.getTipo()))
                .hasSize(1)
                .first()
                .satisfies(a -> {
                    assertThat(a.getDescripcion()).contains("emitida exitosamente");
                    assertThat(a.getDetalle()).isNull(); // detalle null para no exponer datos técnicos del evento
                });
    }

    @Test @Order(4)
    @DisplayName("a04: Evento RECEPCION_CONFIRMADA → tipo GRE_RECEPCION_CONFIRMADA")
    void a04_eventoRecepcionConfirmada_aparece() {
        Long greId = crearGRE(sucursalOrigen, sucursalDestino, "T001", 3);
        crearEvento(greId, "RECEPCION_CONFIRMADA", null);

        List<ActividadRecienteDTO> result = actividad(null);

        assertThat(result)
                .filteredOn(a -> "GRE_RECEPCION_CONFIRMADA".equals(a.getTipo()))
                .as("Debe aparecer RECEPCION_CONFIRMADA")
                .hasSize(1)
                .first()
                .satisfies(a -> assertThat(a.getDescripcion()).contains("recepción confirmada"));
    }

    @Test @Order(5)
    @DisplayName("a05: Evento CANCELADA → tipo GRE_CANCELADA")
    void a05_eventoCancelada_aparece() {
        Long greId = crearGRE(sucursalOrigen, sucursalDestino, "T001", 4);
        crearEvento(greId, "CANCELADA", "Cancelada por usuario");

        List<ActividadRecienteDTO> result = actividad(null);

        assertThat(result)
                .filteredOn(a -> "GRE_CANCELADA".equals(a.getTipo()))
                .as("Debe aparecer GRE_CANCELADA")
                .hasSize(1)
                .first()
                .satisfies(a -> assertThat(a.getDescripcion()).contains("cancelada"));
    }

    @Test @Order(6)
    @DisplayName("a06: Evento ERROR_LUCODE → tipo GRE_ERROR_LUCODE")
    void a06_eventoErrorLucode_aparece() {
        Long greId = crearGRE(sucursalOrigen, sucursalDestino, "T001", 5);
        crearEvento(greId, "ERROR_LUCODE", "Timeout al enviar a SUNAT");

        List<ActividadRecienteDTO> result = actividad(null);

        assertThat(result)
                .filteredOn(a -> "GRE_ERROR_LUCODE".equals(a.getTipo()))
                .as("Debe aparecer GRE_ERROR_LUCODE")
                .hasSize(1)
                .first()
                .satisfies(a -> assertThat(a.getDescripcion()).contains("error de emisión"));
    }

    @Test @Order(7)
    @DisplayName("a07: Evento EMISION_INICIADA → NO aparece (no está en TIPOS_GRE_ACTIVIDAD)")
    void a07_eventoEmisionIniciada_noAparece() {
        Long greId = crearGRE(sucursalOrigen, sucursalDestino, "T001", 6);
        crearEvento(greId, "EMISION_INICIADA", "Iniciando emisión");

        List<ActividadRecienteDTO> result = actividad(null);

        assertThat(result)
                .as("EMISION_INICIADA no debe aparecer en actividad reciente")
                .noneMatch(a -> a.getTipo().startsWith("GRE_"));
    }

    @Test @Order(8)
    @DisplayName("a08: Filtro sucursal — GRE con sucursalOrigen = sucursalId → incluida")
    void a08_filtroSucursal_origenCoincide_estaIncluida() {
        Long greId = crearGRE(sucursalOrigen, sucursalDestino, "T001", 7);
        crearEvento(greId, "EMITIDA", null);

        List<ActividadRecienteDTO> resultConFiltro   = actividad(sucursalOrigen);
        List<ActividadRecienteDTO> resultSinFiltro   = actividad(null);

        assertThat(resultConFiltro)
                .as("Con filtro sucursalOrigen debe incluir la GRE")
                .filteredOn(a -> "GRE_EMITIDA".equals(a.getTipo()))
                .hasSize(1);

        assertThat(resultSinFiltro)
                .as("Sin filtro también debe aparecer")
                .filteredOn(a -> "GRE_EMITIDA".equals(a.getTipo()))
                .hasSize(1);
    }

    @Test @Order(9)
    @DisplayName("a09: Filtro sucursal — GRE con sucursalDestino = sucursalId → incluida (OR)")
    void a09_filtroSucursal_destinoCoincide_estaIncluida() {
        Long greId = crearGRE(sucursalOrigen, sucursalDestino, "T001", 8);
        crearEvento(greId, "RECEPCION_CONFIRMADA", null);

        // Filtramos por destino
        List<ActividadRecienteDTO> result = actividad(sucursalDestino);

        assertThat(result)
                .as("Con filtro sucursalDestino la GRE debe incluirse (filtro OR)")
                .filteredOn(a -> "GRE_RECEPCION_CONFIRMADA".equals(a.getTipo()))
                .hasSize(1);
    }

    @Test @Order(10)
    @DisplayName("a10: Filtro sucursal — GRE de otra sucursal → NO incluida")
    void a10_filtroSucursal_otraSucursal_noEstaIncluida() {
        // GRE entre origen y destino — sucursalOtra no participa
        Long greId = crearGRE(sucursalOrigen, sucursalDestino, "T001", 9);
        crearEvento(greId, "EMITIDA", null);

        List<ActividadRecienteDTO> result = actividad(sucursalOtra);

        assertThat(result)
                .as("GRE de otra sucursal NO debe aparecer al filtrar por sucursalOtra")
                .noneMatch(a -> a.getTipo().startsWith("GRE_"));
    }
}
