package com.stockflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.entity.Suscripcion;
import com.stockflow.entity.WebhookLog;
import com.stockflow.repository.SuscripcionRepository;
import com.stockflow.repository.UsuarioRepository;
import com.stockflow.repository.WebhookLogRepository;
import com.stockflow.service.CulqiService;
import com.stockflow.service.EmailService;
import com.stockflow.service.PlanLimitService;
import com.stockflow.service.SucursalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * F-06 — Scheduler de reconciliación Culqi.
 * Cubre: batch size, eventos SIN_CORRELACION, PROCESADO_PARCIAL,
 * idempotencia (CAS), branch ops y escalada a PENDIENTE_REVISION.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CulqiF06SchedulerTest {

    private static final String SUB_ID     = "sxn_test_scheduler_001";
    private static final String TENANT_ID  = "tenant-botica-01";
    private static final Long   SUS_ID     = 42L;

    @Mock private SuscripcionRepository suscripcionRepository;
    @Mock private UsuarioRepository     usuarioRepository;
    @Mock private WebhookLogRepository  webhookLogRepository;
    @Mock private EmailService          emailService;
    @Mock private SucursalService       sucursalService;
    @Mock private CulqiService          culqiService;
    @Mock private PlanLimitService      planLimitService;
    @Spy  private ObjectMapper          objectMapper = new ObjectMapper();

    @InjectMocks private CulqiWebhookController controller;

    @BeforeEach
    void setUpCommonStubs() {
        // Por defecto el webhook log save retorna el mismo objeto
        when(webhookLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private WebhookLog logSinCorrelacion(int intentosPrevios, String payload) {
        return WebhookLog.builder()
                .id(1L)
                .webhookId("evt_sinCorr_" + intentosPrevios)
                .tipo("subscription.charge.succeeded")
                .estado("SIN_CORRELACION")
                .intentos(intentosPrevios)
                .detalles(payload)
                .fechaProcesamiento(LocalDateTime.now().minusMinutes(10))
                .build();
    }

    private WebhookLog logProcesadoParcial(int intentosPrevios, String tipo, String payload) {
        return WebhookLog.builder()
                .id(2L)
                .webhookId("evt_parcial_" + intentosPrevios)
                .tipo(tipo)
                .estado("PROCESADO_PARCIAL")
                .intentos(intentosPrevios)
                .detalles(payload)
                .fechaProcesamiento(LocalDateTime.now().minusMinutes(10))
                .build();
    }

    private Suscripcion suscripcion(String estado, String planId) {
        return Suscripcion.builder()
                .id(SUS_ID)
                .tenantId(TENANT_ID)
                .planId(planId)
                .estado(estado)
                .preapprovalId(SUB_ID)
                .precioMensual(BigDecimal.valueOf(89.00))
                .createdAt(LocalDateTime.now())
                .build();
    }

    private void stubListasVacias() {
        when(webhookLogRepository.findSinCorrelacionPendientes(anyInt(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(webhookLogRepository.findProcesadoParcialPendientes(anyInt(), any(), any()))
                .thenReturn(Collections.emptyList());
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    /**
     * SCH-01: Sin eventos en ninguna cola → no se llama save ni branch ops.
     */
    @Test
    void sch01_listasVacias_noHaceNada() {
        stubListasVacias();

        controller.reconciliarEventosPendientes();

        verify(webhookLogRepository, never()).save(any());
        verify(sucursalService, never()).bloquearSucursalesAdicionales(any());
        verify(sucursalService, never()).desbloquearSucursalesConLimite(any(), anyInt());
    }

    /**
     * SCH-02: Evento SIN_CORRELACION con subscription_id en payload.
     * Se encuentra la suscripción (TRIAL), el CAS activa la suscripción → PROCESADO.
     */
    @Test
    void sch02_eventoSinCorrelacion_subscriptionIdEnPayload_marcaProcesado() {
        String payload = "{\"subscription_id\":\"" + SUB_ID + "\"}";
        WebhookLog logEntry = logSinCorrelacion(0, payload);
        Suscripcion sus = suscripcion("TRIAL", "BÁSICO");

        when(webhookLogRepository.findSinCorrelacionPendientes(anyInt(), any(), any()))
                .thenReturn(List.of(logEntry));
        when(webhookLogRepository.findProcesadoParcialPendientes(anyInt(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(suscripcionRepository.findByPreapprovalId(SUB_ID)).thenReturn(Optional.of(sus));
        when(suscripcionRepository.updateEstadoIfPreapprovalIdMatches(SUS_ID, "ACTIVA", SUB_ID))
                .thenReturn(1);
        when(planLimitService.getMaxSucursales(TENANT_ID)).thenReturn(1);
        when(sucursalService.desbloquearSucursalesConLimite(any(), anyInt())).thenReturn(0);

        controller.reconciliarEventosPendientes();

        ArgumentCaptor<WebhookLog> captor = ArgumentCaptor.forClass(WebhookLog.class);
        verify(webhookLogRepository, atLeastOnce()).save(captor.capture());
        List<WebhookLog> saves = captor.getAllValues();
        assertThat(saves).anyMatch(w -> "PROCESADO".equals(w.getEstado()));
    }

    /**
     * SCH-03: Evento SIN_CORRELACION agota MAX_INTENTOS sin encontrar suscripción → PENDIENTE_REVISION.
     */
    @Test
    void sch03_eventoSinCorrelacion_agotaIntentos_marcaPendienteRevision() {
        // intentos = MAX-1 → después de incremento = MAX → condición se cumple
        int intentosPrevios = CulqiWebhookController.MAX_INTENTOS_RECONCILIACION - 1;
        String payload = "{\"subscription_id\":\"sxn_no_existe_999\"}";
        WebhookLog logEntry = logSinCorrelacion(intentosPrevios, payload);

        when(webhookLogRepository.findSinCorrelacionPendientes(anyInt(), any(), any()))
                .thenReturn(List.of(logEntry));
        when(webhookLogRepository.findProcesadoParcialPendientes(anyInt(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(suscripcionRepository.findByPreapprovalId(anyString())).thenReturn(Optional.empty());

        controller.reconciliarEventosPendientes();

        ArgumentCaptor<WebhookLog> captor = ArgumentCaptor.forClass(WebhookLog.class);
        verify(webhookLogRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues())
                .anyMatch(w -> "PENDIENTE_REVISION".equals(w.getEstado()));
    }

    /**
     * SCH-04: Evento SIN_CORRELACION sin payload almacenado → PENDIENTE_REVISION inmediato.
     */
    @Test
    void sch04_eventoSinCorrelacion_sinPayload_marcaPendienteRevision() {
        WebhookLog logEntry = logSinCorrelacion(0, null);

        when(webhookLogRepository.findSinCorrelacionPendientes(anyInt(), any(), any()))
                .thenReturn(List.of(logEntry));
        when(webhookLogRepository.findProcesadoParcialPendientes(anyInt(), any(), any()))
                .thenReturn(Collections.emptyList());

        controller.reconciliarEventosPendientes();

        ArgumentCaptor<WebhookLog> captor = ArgumentCaptor.forClass(WebhookLog.class);
        verify(webhookLogRepository).save(captor.capture());
        assertThat(captor.getValue().getEstado()).isEqualTo("PENDIENTE_REVISION");
        // No se buscó suscripción
        verify(suscripcionRepository, never()).findByPreapprovalId(any());
    }

    /**
     * SCH-05: Evento PROCESADO_PARCIAL tipo charge.failed, suscripción SUSPENDIDA en plan PRO.
     * Debe bloquear sucursales adicionales y marcar PROCESADO.
     */
    @Test
    void sch05_procesadoParcialFallido_suspendidaPro_bloqueaSucursales() {
        String payload = "{\"subscription_id\":\"" + SUB_ID + "\"}";
        WebhookLog logEntry = logProcesadoParcial(0, "subscription.charge.failed", payload);
        Suscripcion sus = suscripcion("SUSPENDIDA", "PRO");

        when(webhookLogRepository.findSinCorrelacionPendientes(anyInt(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(webhookLogRepository.findProcesadoParcialPendientes(anyInt(), any(), any()))
                .thenReturn(List.of(logEntry));
        when(suscripcionRepository.findByPreapprovalId(SUB_ID)).thenReturn(Optional.of(sus));
        when(sucursalService.bloquearSucursalesAdicionales(TENANT_ID)).thenReturn(1);

        controller.reconciliarEventosPendientes();

        verify(sucursalService).bloquearSucursalesAdicionales(TENANT_ID);
        ArgumentCaptor<WebhookLog> captor = ArgumentCaptor.forClass(WebhookLog.class);
        verify(webhookLogRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues())
                .anyMatch(w -> "PROCESADO".equals(w.getEstado()));
    }

    /**
     * SCH-06: Evento PROCESADO_PARCIAL tipo charge.succeeded, suscripción ACTIVA.
     * Debe desbloquear sucursales con límite del plan y marcar PROCESADO.
     */
    @Test
    void sch06_procesadoParcialExitoso_activaBasico_desbloqueaSucursales() {
        String payload = "{\"subscription_id\":\"" + SUB_ID + "\"}";
        WebhookLog logEntry = logProcesadoParcial(0, "subscription.charge.succeeded", payload);
        Suscripcion sus = suscripcion("ACTIVA", "BÁSICO");

        when(webhookLogRepository.findSinCorrelacionPendientes(anyInt(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(webhookLogRepository.findProcesadoParcialPendientes(anyInt(), any(), any()))
                .thenReturn(List.of(logEntry));
        when(suscripcionRepository.findByPreapprovalId(SUB_ID)).thenReturn(Optional.of(sus));
        when(planLimitService.getMaxSucursales(TENANT_ID)).thenReturn(1);
        when(sucursalService.desbloquearSucursalesConLimite(TENANT_ID, 1)).thenReturn(0);

        controller.reconciliarEventosPendientes();

        verify(planLimitService).getMaxSucursales(TENANT_ID);
        verify(sucursalService).desbloquearSucursalesConLimite(TENANT_ID, 1);
        ArgumentCaptor<WebhookLog> captor = ArgumentCaptor.forClass(WebhookLog.class);
        verify(webhookLogRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues())
                .anyMatch(w -> "PROCESADO".equals(w.getEstado()));
    }

    /**
     * SCH-07: PROCESADO_PARCIAL tipo charge.failed pero la suscripción ya no está SUSPENDIDA
     * (fue resuelta por otro medio). Debe marcar PROCESADO sin branch op.
     */
    @Test
    void sch07_procesadoParcial_suscripcionYaResuelta_marcaProcesadoSinBranchOp() {
        String payload = "{\"subscription_id\":\"" + SUB_ID + "\"}";
        WebhookLog logEntry = logProcesadoParcial(0, "subscription.charge.failed", payload);
        // Suscripción ya fue reactivada externamente
        Suscripcion sus = suscripcion("ACTIVA", "BÁSICO");

        when(webhookLogRepository.findSinCorrelacionPendientes(anyInt(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(webhookLogRepository.findProcesadoParcialPendientes(anyInt(), any(), any()))
                .thenReturn(List.of(logEntry));
        when(suscripcionRepository.findByPreapprovalId(SUB_ID)).thenReturn(Optional.of(sus));

        controller.reconciliarEventosPendientes();

        // No se bloqueó nada
        verify(sucursalService, never()).bloquearSucursalesAdicionales(any());
        ArgumentCaptor<WebhookLog> captor = ArgumentCaptor.forClass(WebhookLog.class);
        verify(webhookLogRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues())
                .anyMatch(w -> "PROCESADO".equals(w.getEstado()));
    }

    /**
     * SCH-08: PROCESADO_PARCIAL agota MAX_INTENTOS → PENDIENTE_REVISION sin ejecutar branch op.
     */
    @Test
    void sch08_procesadoParcial_agotaIntentos_marcaPendienteRevision() {
        int intentosPrevios = CulqiWebhookController.MAX_INTENTOS_RECONCILIACION - 1;
        String payload = "{\"subscription_id\":\"" + SUB_ID + "\"}";
        WebhookLog logEntry = logProcesadoParcial(intentosPrevios, "subscription.charge.failed", payload);

        when(webhookLogRepository.findSinCorrelacionPendientes(anyInt(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(webhookLogRepository.findProcesadoParcialPendientes(anyInt(), any(), any()))
                .thenReturn(List.of(logEntry));

        controller.reconciliarEventosPendientes();

        // No se buscó suscripción ni se ejecutaron branch ops
        verify(suscripcionRepository, never()).findByPreapprovalId(any());
        verify(sucursalService, never()).bloquearSucursalesAdicionales(any());
        ArgumentCaptor<WebhookLog> captor = ArgumentCaptor.forClass(WebhookLog.class);
        verify(webhookLogRepository).save(captor.capture());
        assertThat(captor.getValue().getEstado()).isEqualTo("PENDIENTE_REVISION");
    }

    /**
     * SCH-09: Branch op lanza excepción → evento se mantiene PROCESADO_PARCIAL para próximo ciclo.
     * El contador de intentos sí se incrementa.
     */
    @Test
    void sch09_branchOpLanzaExcepcion_mantieneProcesadoParcial() {
        String payload = "{\"subscription_id\":\"" + SUB_ID + "\"}";
        WebhookLog logEntry = logProcesadoParcial(0, "subscription.charge.failed", payload);
        Suscripcion sus = suscripcion("SUSPENDIDA", "PRO");

        when(webhookLogRepository.findSinCorrelacionPendientes(anyInt(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(webhookLogRepository.findProcesadoParcialPendientes(anyInt(), any(), any()))
                .thenReturn(List.of(logEntry));
        when(suscripcionRepository.findByPreapprovalId(SUB_ID)).thenReturn(Optional.of(sus));
        when(sucursalService.bloquearSucursalesAdicionales(TENANT_ID))
                .thenThrow(new RuntimeException("DB timeout"));

        controller.reconciliarEventosPendientes();

        ArgumentCaptor<WebhookLog> captor = ArgumentCaptor.forClass(WebhookLog.class);
        verify(webhookLogRepository, atLeastOnce()).save(captor.capture());
        // El estado vuelve a PROCESADO_PARCIAL para que el próximo ciclo lo reintente
        assertThat(captor.getAllValues())
                .anyMatch(w -> "PROCESADO_PARCIAL".equals(w.getEstado()));
        // El contador fue incrementado a 1
        assertThat(captor.getAllValues())
                .anyMatch(w -> "PROCESADO_PARCIAL".equals(w.getEstado()) && w.getIntentos() == 1);
    }

    /**
     * SCH-10: El scheduler pasa un PageRequest con pageSize = BATCH_SIZE_SCHEDULER a ambas queries.
     * Garantiza que nunca se cargan más de BATCH_SIZE eventos por ciclo.
     */
    @Test
    void sch10_schedulerPasaPageRequestConBatchSize() {
        stubListasVacias();

        controller.reconciliarEventosPendientes();

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(webhookLogRepository).findSinCorrelacionPendientes(anyInt(), any(), captor.capture());
        assertThat(captor.getValue().getPageSize())
                .isEqualTo(CulqiWebhookController.BATCH_SIZE_SCHEDULER);

        ArgumentCaptor<Pageable> captor2 = ArgumentCaptor.forClass(Pageable.class);
        verify(webhookLogRepository).findProcesadoParcialPendientes(anyInt(), any(), captor2.capture());
        assertThat(captor2.getValue().getPageSize())
                .isEqualTo(CulqiWebhookController.BATCH_SIZE_SCHEDULER);
    }

    /**
     * SCH-11: CAS con preapprovalId reemplazado en reconciliarEvento (charge.succeeded).
     * updateEstadoIfPreapprovalIdMatches retorna 0 → la suscripción no se modifica → IGNORADO.
     * Protege contra actualizaciones concurrentes de upgrade/downgrade.
     */
    @Test
    void sch11_cas_preapprovalReemplazado_marcaIgnorado() {
        String payload = "{\"subscription_id\":\"" + SUB_ID + "\"}";
        WebhookLog logEntry = logSinCorrelacion(0, payload);
        Suscripcion sus = suscripcion("TRIAL", "BÁSICO");

        when(webhookLogRepository.findSinCorrelacionPendientes(anyInt(), any(), any()))
                .thenReturn(List.of(logEntry));
        when(webhookLogRepository.findProcesadoParcialPendientes(anyInt(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(suscripcionRepository.findByPreapprovalId(SUB_ID)).thenReturn(Optional.of(sus));
        // CAS retorna 0: preapprovalId fue reemplazado por otro webhook concurrente
        when(suscripcionRepository.updateEstadoIfPreapprovalIdMatches(SUS_ID, "ACTIVA", SUB_ID))
                .thenReturn(0);

        controller.reconciliarEventosPendientes();

        // La suscripción NO se guardó (el CAS falló)
        verify(suscripcionRepository, never()).save(any());
        ArgumentCaptor<WebhookLog> captor = ArgumentCaptor.forClass(WebhookLog.class);
        verify(webhookLogRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues())
                .anyMatch(w -> "IGNORADO".equals(w.getEstado()));
    }
}
