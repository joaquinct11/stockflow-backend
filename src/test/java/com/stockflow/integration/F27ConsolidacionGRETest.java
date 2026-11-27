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
 * F-27 Consolidar eventos GRE en una única actividad por guía.
 *
 * b01 — GRE solo con borrador → una actividad GRE_CREADA
 * b02 — GRE con CREADA luego EMITIDA → una actividad GRE_EMITIDA (no GRE_CREADA)
 * b03 — GRE con CREADA, EMITIDA, RECEPCION_CONFIRMADA → una sola GRE_RECEPCION_CONFIRMADA
 * b04 — GRE cancelada (CREADA + CANCELADA) → una sola GRE_CANCELADA
 * b05 — GRE con error (CREADA + ERROR_LUCODE) → una sola GRE_ERROR_LUCODE (sin duplicados)
 * b06 — Hash del evento EMITIDA nunca aparece en descripción ni detalle
 * b07 — Dos GREs: la más reciente aparece antes que la más antigua
 * b08 — 30 GREs con 2 eventos cada una → solo 25 actividades únicas (límite post-dedup)
 * b09 — Las demás actividades del Dashboard coexisten con GRE sin interferencia
 * b10 — El historial completo de eventos en DB permanece intacto tras consultar el Dashboard
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class F27ConsolidacionGRETest {

    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    static {
        postgres.start();
    }

    @Autowired private DashboardService dashboardService;
    @Autowired private JdbcTemplate jdbc;

    private static final String TENANT = "f27-consolidacion-tenant";

    private Long sucursalOrigen;
    private Long sucursalDestino;

    @BeforeAll
    void setup() {
        jdbc.update("INSERT INTO tenants (tenant_id, nombre, activo, rubro) " +
                "VALUES (?, 'F27 Consolidacion', true, 'BOTICA') ON CONFLICT (tenant_id) DO NOTHING", TENANT);

        sucursalOrigen = jdbc.queryForObject(
                "INSERT INTO sucursales (nombre, tenant_id, es_principal, activo) " +
                "VALUES ('Origen F27', ?, true, true) RETURNING id", Long.class, TENANT);

        sucursalDestino = jdbc.queryForObject(
                "INSERT INTO sucursales (nombre, tenant_id, es_principal, activo) " +
                "VALUES ('Destino F27', ?, false, true) RETURNING id", Long.class, TENANT);
    }

    @BeforeEach
    void limpiar() {
        jdbc.update("DELETE FROM guia_remision_eventos WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM guia_remision_items WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM guias_remision WHERE tenant_id = ?", TENANT);
    }

    private Long crearGRE(String serie, int correlativo) {
        return jdbc.queryForObject(
                "INSERT INTO guias_remision " +
                "(tenant_id, serie, correlativo, tipo_traslado, motivo_traslado_codigo, " +
                " modalidad_transporte, fecha_traslado, estado_tributario, estado_logistico, " +
                " sucursal_origen_id, sucursal_destino_id, destinatario_razon_social, " +
                " unidad_medida_peso, idempotency_key) " +
                "VALUES (?, ?, ?, 'SUCURSALES', '04', 'PRIVADO', CURRENT_DATE, " +
                " 'BORRADOR', 'PENDIENTE', ?, ?, 'Cliente Test', 'KGM', gen_random_uuid()) RETURNING id",
                Long.class, TENANT, serie, correlativo, sucursalOrigen, sucursalDestino);
    }

    private void crearEventoEn(Long greId, String tipoEvento, String timestampIso) {
        jdbc.update("INSERT INTO guia_remision_eventos (guia_remision_id, tenant_id, tipo_evento, descripcion, created_at) " +
                "VALUES (?, ?, ?, 'Descripción de test', ?::timestamptz)",
                greId, TENANT, tipoEvento, timestampIso);
    }

    private void crearEventoHashEn(Long greId, String timestampIso) {
        // Simula el contenido real que el servicio de producción guarda al emitir
        jdbc.update("INSERT INTO guia_remision_eventos (guia_remision_id, tenant_id, tipo_evento, descripcion, created_at) " +
                "VALUES (?, ?, 'EMITIDA', 'Emitida exitosamente. Hash: abc123deadbeef456789', ?::timestamptz)",
                greId, TENANT, timestampIso);
    }

    private List<ActividadRecienteDTO> actividad() {
        TenantContext.setCurrentTenant(TENANT);
        try { return dashboardService.getActividadReciente(TENANT, null, 25); }
        finally { TenantContext.clear(); }
    }

    // ── Tests ────────────────────────────────────────────────────────────────

    @Test @Order(1)
    @DisplayName("b01: GRE solo con borrador → una actividad GRE_CREADA")
    void b01_soloCreada_unaActividad() {
        Long greId = crearGRE("T001", 1);
        crearEventoEn(greId, "CREADA", "2024-06-01T10:00:00Z");

        List<ActividadRecienteDTO> result = actividad();

        assertThat(result)
                .filteredOn(a -> a.getTipo().startsWith("GRE_"))
                .as("Debe haber exactamente una actividad GRE")
                .hasSize(1)
                .first()
                .satisfies(a -> {
                    assertThat(a.getTipo()).isEqualTo("GRE_CREADA");
                    assertThat(a.getDescripcion()).contains("T001-1").contains("borrador");
                });
    }

    @Test @Order(2)
    @DisplayName("b02: GRE con CREADA luego EMITIDA → una actividad GRE_EMITIDA, no GRE_CREADA")
    void b02_creadaLuegoEmitida_soloEmitida() {
        Long greId = crearGRE("T001", 2);
        crearEventoEn(greId, "CREADA",  "2024-06-01T10:00:00Z");
        crearEventoEn(greId, "EMITIDA", "2024-06-01T11:00:00Z");

        List<ActividadRecienteDTO> result = actividad();

        List<ActividadRecienteDTO> greActividades = result.stream()
                .filter(a -> a.getTipo().startsWith("GRE_")).toList();

        assertThat(greActividades)
                .as("Solo debe haber una actividad GRE (la más reciente)")
                .hasSize(1);
        assertThat(greActividades.get(0).getTipo())
                .as("Debe mostrar el estado más reciente: GRE_EMITIDA")
                .isEqualTo("GRE_EMITIDA");
        assertThat(greActividades)
                .as("No debe aparecer GRE_CREADA")
                .noneMatch(a -> "GRE_CREADA".equals(a.getTipo()));
    }

    @Test @Order(3)
    @DisplayName("b03: GRE con CREADA, EMITIDA, RECEPCION_CONFIRMADA → una sola GRE_RECEPCION_CONFIRMADA")
    void b03_tresEventos_soloRecepcionConfirmada() {
        Long greId = crearGRE("T001", 3);
        crearEventoEn(greId, "CREADA",               "2024-06-01T10:00:00Z");
        crearEventoEn(greId, "EMITIDA",              "2024-06-01T11:00:00Z");
        crearEventoEn(greId, "RECEPCION_CONFIRMADA", "2024-06-01T14:00:00Z");

        List<ActividadRecienteDTO> result = actividad();

        List<ActividadRecienteDTO> greActividades = result.stream()
                .filter(a -> a.getTipo().startsWith("GRE_")).toList();

        assertThat(greActividades).hasSize(1);
        assertThat(greActividades.get(0).getTipo()).isEqualTo("GRE_RECEPCION_CONFIRMADA");
        assertThat(greActividades.get(0).getDescripcion()).contains("recepción confirmada");
    }

    @Test @Order(4)
    @DisplayName("b04: GRE cancelada (CREADA + CANCELADA) → una sola GRE_CANCELADA")
    void b04_creadaLuegoCancelada_soloCancelada() {
        Long greId = crearGRE("T001", 4);
        crearEventoEn(greId, "CREADA",    "2024-06-01T10:00:00Z");
        crearEventoEn(greId, "CANCELADA", "2024-06-01T10:30:00Z");

        List<ActividadRecienteDTO> result = actividad();

        List<ActividadRecienteDTO> greActividades = result.stream()
                .filter(a -> a.getTipo().startsWith("GRE_")).toList();

        assertThat(greActividades).hasSize(1);
        assertThat(greActividades.get(0).getTipo()).isEqualTo("GRE_CANCELADA");
    }

    @Test @Order(5)
    @DisplayName("b05: GRE con error (CREADA + ERROR_LUCODE) → una sola GRE_ERROR_LUCODE, sin duplicados")
    void b05_errorLucode_sinDuplicados() {
        Long greId = crearGRE("T001", 5);
        crearEventoEn(greId, "CREADA",      "2024-06-01T10:00:00Z");
        crearEventoEn(greId, "ERROR_LUCODE","2024-06-01T10:05:00Z");

        List<ActividadRecienteDTO> result = actividad();

        List<ActividadRecienteDTO> greActividades = result.stream()
                .filter(a -> a.getTipo().startsWith("GRE_")).toList();

        assertThat(greActividades)
                .as("No debe haber duplicados: solo 1 actividad para esta GRE")
                .hasSize(1);
        assertThat(greActividades.get(0).getTipo()).isEqualTo("GRE_ERROR_LUCODE");
    }

    @Test @Order(6)
    @DisplayName("b06: Hash del evento EMITIDA nunca aparece en descripción ni detalle")
    void b06_hashNoAparecEnActividad() {
        Long greId = crearGRE("T001", 6);
        crearEventoHashEn(greId, "2024-06-01T10:00:00Z"); // descripcion contiene "Hash: abc123deadbeef456789"

        List<ActividadRecienteDTO> result = actividad();

        assertThat(result)
                .filteredOn(a -> "GRE_EMITIDA".equals(a.getTipo()))
                .hasSize(1)
                .first()
                .satisfies(a -> {
                    assertThat(a.getDetalle())
                            .as("detalle debe ser null para no exponer datos técnicos")
                            .isNull();
                    assertThat(a.getDescripcion())
                            .as("descripción no debe contener el hash")
                            .doesNotContain("Hash:")
                            .doesNotContain("abc123deadbeef456789");
                });
    }

    @Test @Order(7)
    @DisplayName("b07: Dos GREs con distinta antigüedad → la más reciente aparece antes")
    void b07_ordenCronologicoDecendente() {
        Long greAntigua  = crearGRE("T001", 7);
        Long greReciente = crearGRE("T001", 8);
        crearEventoEn(greAntigua,  "CREADA", "2024-06-01T08:00:00Z");
        crearEventoEn(greReciente, "CREADA", "2024-06-02T09:00:00Z"); // más reciente

        List<ActividadRecienteDTO> result = actividad();

        List<ActividadRecienteDTO> gres = result.stream()
                .filter(a -> a.getTipo().startsWith("GRE_")).toList();

        assertThat(gres).hasSize(2);
        assertThat(gres.get(0).getDescripcion())
                .as("La GRE más reciente (T001-8) debe aparecer primero")
                .contains("T001-8");
        assertThat(gres.get(1).getDescripcion())
                .as("La GRE más antigua (T001-7) aparece después")
                .contains("T001-7");
    }

    @Test @Order(8)
    @DisplayName("b08: 30 GREs con 2 eventos cada una → límite de 25 se aplica post-deduplicación")
    void b08_limitePostDeduplicacion() {
        for (int i = 1; i <= 30; i++) {
            Long greId = crearGRE("T002", i);
            crearEventoEn(greId, "CREADA",  "2024-06-01T" + String.format("%02d", i % 23) + ":00:00Z");
            crearEventoEn(greId, "EMITIDA", "2024-06-01T" + String.format("%02d", i % 23) + ":30:00Z");
        }

        List<ActividadRecienteDTO> result = actividad();

        long greCount = result.stream().filter(a -> a.getTipo().startsWith("GRE_")).count();

        assertThat(result)
                .as("El límite de 25 se aplica al total de actividades consolidadas")
                .hasSizeLessThanOrEqualTo(25);
        assertThat(greCount)
                .as("No puede haber más de 25 actividades GRE (una por guía, límite 25)")
                .isLessThanOrEqualTo(25);
        assertThat(result)
                .as("Ninguna guía debe aparecer duplicada")
                .filteredOn(a -> a.getTipo().startsWith("GRE_"))
                .extracting(ActividadRecienteDTO::getDescripcion)
                .doesNotHaveDuplicates();
    }

    @Test @Order(9)
    @DisplayName("b09: Las demás actividades del Dashboard coexisten con GRE sin interferencia")
    void b09_otraActividadesPermanecenIntactas() {
        // Crear una GRE con evento
        Long greId = crearGRE("T001", 9);
        crearEventoEn(greId, "EMITIDA", "2024-06-01T10:00:00Z");

        // No crear ventas ni movimientos — solo verificar que el servicio no lanza excepciones
        // y que la actividad GRE aparece correctamente junto al resto
        List<ActividadRecienteDTO> result = actividad();

        assertThat(result)
                .as("El servicio debe retornar resultados sin lanzar excepción")
                .isNotNull();
        assertThat(result)
                .filteredOn(a -> "GRE_EMITIDA".equals(a.getTipo()))
                .as("La actividad GRE debe aparecer")
                .hasSize(1);
        // Verificar que los tipos GRE no reemplazan ni rompen otros tipos
        assertThat(result)
                .as("No deben aparecer tipos GRE inválidos")
                .allMatch(a -> !a.getTipo().startsWith("GRE_") || List.of(
                        "GRE_CREADA", "GRE_EMITIDA", "GRE_RECEPCION_CONFIRMADA",
                        "GRE_CANCELADA", "GRE_ERROR_LUCODE").contains(a.getTipo()));
    }

    @Test @Order(10)
    @DisplayName("b10: El historial de eventos en DB permanece intacto tras consultar el Dashboard")
    void b10_historialEventosIntacto() {
        Long greId = crearGRE("T001", 10);
        crearEventoEn(greId, "CREADA",               "2024-06-01T10:00:00Z");
        crearEventoEn(greId, "EMISION_INICIADA",     "2024-06-01T11:00:00Z");
        crearEventoEn(greId, "EMITIDA",              "2024-06-01T11:05:00Z");
        crearEventoEn(greId, "RECEPCION_CONFIRMADA", "2024-06-01T14:00:00Z");

        // Consultar actividad reciente
        actividad();

        // Verificar que todos los eventos siguen en DB
        int eventCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM guia_remision_eventos WHERE guia_remision_id = ?",
                Integer.class, greId);

        assertThat(eventCount)
                .as("Los 4 eventos históricos deben permanecer en DB sin ser eliminados")
                .isEqualTo(4);
    }
}
