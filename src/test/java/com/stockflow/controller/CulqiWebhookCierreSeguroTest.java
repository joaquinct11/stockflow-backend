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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.stockflow.entity.Usuario;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Cierre seguro de Fase 2:
 *   A — PROCESADO_PARCIAL: email antes del branch op; reconciliador reintenta solo el branch.
 *   B — CAS: updateEstadoIfPreapprovalIdMatches protege contra race de preapprovalId reemplazado.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Cierre seguro Fase 2 — PROCESADO_PARCIAL y CAS de preapprovalId")
class CulqiWebhookCierreSeguroTest {

    private static final String TENANT      = "vet-fonavi";
    private static final String SUB_ID      = "sxn_pro_123";
    private static final String EVT_FAILED  = "subscription.charge.failed";
    private static final String EVT_SUCCESS = "subscription.charge.succeeded";

    private final Usuario usuarioPrincipal = Usuario.builder()
            .id(1L).email("admin@vet-fonavi.com").nombre("Admin")
            .activo(true).build();

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
    void setUp() {
        when(webhookLogRepository.existsByWebhookIdAndEstado(anyString(), eq("PROCESADO"))).thenReturn(false);
        when(webhookLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(suscripcionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // CAS por defecto: preapprovalId vigente. Tests de race sobreescriben con 0.
        when(suscripcionRepository.updateEstadoIfPreapprovalIdMatches(any(), any(), any())).thenReturn(1);
        // findSinCorrelacionPendientes: vacío por defecto (solo los tests de reconciliación lo sobreescriben)
        when(webhookLogRepository.findSinCorrelacionPendientes(anyInt(), any())).thenReturn(List.of());
        // findProcesadoParcialPendientes: vacío por defecto
        when(webhookLogRepository.findProcesadoParcialPendientes(anyInt(), any())).thenReturn(List.of());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Suscripcion suscripcionPro(String estado) {
        return Suscripcion.builder()
                .id(5L).tenantId(TENANT).planId("PRO")
                .preapprovalId(SUB_ID).estado(estado)
                .precioMensual(new BigDecimal("199.00"))
                .usuarioPrincipal(usuarioPrincipal)
                .fechaInicio(LocalDateTime.now().minusMonths(1))
                .currentPeriodStart(LocalDateTime.now().minusMonths(1))
                .currentPeriodEnd(LocalDateTime.now().plusMonths(1))
                .build();
    }

    private com.stockflow.dto.CulqiWebhookDTO webhookDTO(String type, String subscriptionId) {
        com.stockflow.dto.CulqiWebhookDTO dto = new com.stockflow.dto.CulqiWebhookDTO();
        dto.setId("evt_" + System.nanoTime());
        dto.setType(type);
        dto.setData("{\"subscription_id\":\"" + subscriptionId + "\"}");
        return dto;
    }

    // ── A — PROCESADO_PARCIAL en el handler ──────────────────────────────────

    @Nested
    @DisplayName("A — PROCESADO_PARCIAL cuando la branch op falla")
    class ProcesadoParcial {

        @Test
        @DisplayName("charge.failed PRO: bloqueo falla → PROCESADO_PARCIAL; email ya enviado antes del fallo")
        void chargeFallido_bloqueoFalla_marcaProcesadoParcial_emailYaEnviado() {
            Suscripcion s = suscripcionPro("ACTIVA");
            when(suscripcionRepository.findByPreapprovalId(SUB_ID)).thenReturn(Optional.of(s));
            when(sucursalService.bloquearSucursalesAdicionales(TENANT))
                    .thenThrow(new RuntimeException("BD no disponible — bloqueo fallido"));

            controller.recibirWebhook(webhookDTO(EVT_FAILED, SUB_ID));

            // Email debe haberse enviado ANTES del fallo del branch op
            verify(emailService).enviarEmailSuscripcion(any(), any(), eq("SUSPENDIDA"), any());

            // Log debe quedar PROCESADO_PARCIAL (no PROCESADO, no ERROR)
            ArgumentCaptor<WebhookLog> capLog = ArgumentCaptor.forClass(WebhookLog.class);
            verify(webhookLogRepository, atLeast(2)).save(capLog.capture());
            assertThat(capLog.getAllValues())
                    .anyMatch(l -> "PROCESADO_PARCIAL".equals(l.getEstado()));
            assertThat(capLog.getAllValues())
                    .noneMatch(l -> "PROCESADO".equals(l.getEstado()));
        }

        @Test
        @DisplayName("charge.succeeded PRO: desbloqueo falla → PROCESADO_PARCIAL; email ya enviado antes del fallo")
        void chargeExitoso_desbloqueoFalla_marcaProcesadoParcial_emailYaEnviado() {
            Suscripcion s = suscripcionPro("SUSPENDIDA");
            when(suscripcionRepository.findByPreapprovalId(SUB_ID)).thenReturn(Optional.of(s));
            when(planLimitService.getMaxSucursales(TENANT)).thenReturn(5);
            when(sucursalService.desbloquearSucursalesConLimite(TENANT, 5))
                    .thenThrow(new RuntimeException("BD no disponible — desbloqueo fallido"));

            controller.recibirWebhook(webhookDTO(EVT_SUCCESS, SUB_ID));

            // Email debe haberse enviado ANTES del fallo
            verify(emailService).enviarEmailSuscripcion(any(), any(), eq("ACTIVA"), any());

            ArgumentCaptor<WebhookLog> capLog = ArgumentCaptor.forClass(WebhookLog.class);
            verify(webhookLogRepository, atLeast(2)).save(capLog.capture());
            assertThat(capLog.getAllValues())
                    .anyMatch(l -> "PROCESADO_PARCIAL".equals(l.getEstado()));
            assertThat(capLog.getAllValues())
                    .noneMatch(l -> "PROCESADO".equals(l.getEstado()));
        }

        @Test
        @DisplayName("charge.failed PRO: branch op exitosa → PROCESADO (no PROCESADO_PARCIAL cuando no hay fallo)")
        void chargeFallido_bloqueoExitoso_marcaProcesado_noMarcaParcial() {
            Suscripcion s = suscripcionPro("ACTIVA");
            when(suscripcionRepository.findByPreapprovalId(SUB_ID)).thenReturn(Optional.of(s));
            when(sucursalService.bloquearSucursalesAdicionales(TENANT)).thenReturn(2);

            controller.recibirWebhook(webhookDTO(EVT_FAILED, SUB_ID));

            ArgumentCaptor<WebhookLog> capLog = ArgumentCaptor.forClass(WebhookLog.class);
            verify(webhookLogRepository, atLeast(2)).save(capLog.capture());
            assertThat(capLog.getAllValues())
                    .anyMatch(l -> "PROCESADO".equals(l.getEstado()));
            assertThat(capLog.getAllValues())
                    .noneMatch(l -> "PROCESADO_PARCIAL".equals(l.getEstado()));
        }
    }

    // ── A — Reconciliador reintenta PROCESADO_PARCIAL ────────────────────────

    @Nested
    @DisplayName("A — Reconciliador: reintento de branch op sin re-enviar email")
    class ReconciliacionBranchOp {

        private WebhookLog logParcial(String tipo, int intentos) {
            return WebhookLog.builder()
                    .id(99L)
                    .webhookId("evt_parcial_" + System.nanoTime())
                    .tipo(tipo)
                    .estado("PROCESADO_PARCIAL")
                    .intentos(intentos)
                    .detalles("{\"subscription_id\":\"" + SUB_ID + "\"}")
                    .fechaProcesamiento(LocalDateTime.now().minusMinutes(10))
                    .build();
        }

        @Test
        @DisplayName("charge.failed PROCESADO_PARCIAL — suscripción sigue SUSPENDIDA → bloquea y marca PROCESADO")
        void parcialFallido_suspenida_bloqueaYmarcaProcesado() {
            WebhookLog log = logParcial(EVT_FAILED, 0);
            Suscripcion s = suscripcionPro("SUSPENDIDA");
            when(webhookLogRepository.findProcesadoParcialPendientes(anyInt(), any()))
                    .thenReturn(List.of(log));
            when(suscripcionRepository.findByPreapprovalId(SUB_ID)).thenReturn(Optional.of(s));
            when(sucursalService.bloquearSucursalesAdicionales(TENANT)).thenReturn(2);

            controller.reconciliarEventosPendientes();

            verify(sucursalService).bloquearSucursalesAdicionales(TENANT);
            // No re-envía email
            verify(emailService, never()).enviarEmailSuscripcion(any(), any(), any(), any());
            // Queda PROCESADO
            ArgumentCaptor<WebhookLog> capLog = ArgumentCaptor.forClass(WebhookLog.class);
            verify(webhookLogRepository).save(capLog.capture());
            assertThat(capLog.getValue().getEstado()).isEqualTo("PROCESADO");
            assertThat(capLog.getValue().getIntentos()).isEqualTo(1);
        }

        @Test
        @DisplayName("charge.succeeded PROCESADO_PARCIAL — suscripción sigue ACTIVA → desbloquea y marca PROCESADO")
        void parcialExitoso_activa_desbloqueoYmarcaProcesado() {
            WebhookLog log = logParcial(EVT_SUCCESS, 0);
            Suscripcion s = suscripcionPro("ACTIVA");
            when(webhookLogRepository.findProcesadoParcialPendientes(anyInt(), any()))
                    .thenReturn(List.of(log));
            when(suscripcionRepository.findByPreapprovalId(SUB_ID)).thenReturn(Optional.of(s));
            when(planLimitService.getMaxSucursales(TENANT)).thenReturn(5);
            when(sucursalService.desbloquearSucursalesConLimite(TENANT, 5)).thenReturn(2);

            controller.reconciliarEventosPendientes();

            verify(sucursalService).desbloquearSucursalesConLimite(TENANT, 5);
            verify(emailService, never()).enviarEmailSuscripcion(any(), any(), any(), any());
            ArgumentCaptor<WebhookLog> capLog = ArgumentCaptor.forClass(WebhookLog.class);
            verify(webhookLogRepository).save(capLog.capture());
            assertThat(capLog.getValue().getEstado()).isEqualTo("PROCESADO");
        }

        @Test
        @DisplayName("charge.failed PROCESADO_PARCIAL — suscripción ya no está SUSPENDIDA → PROCESADO sin bloquear")
        void parcialFallido_susNoSuspendida_marcaProcesadoSinBloquear() {
            WebhookLog log = logParcial(EVT_FAILED, 0);
            Suscripcion s = suscripcionPro("ACTIVA"); // ya recuperada por otro medio
            when(webhookLogRepository.findProcesadoParcialPendientes(anyInt(), any()))
                    .thenReturn(List.of(log));
            when(suscripcionRepository.findByPreapprovalId(SUB_ID)).thenReturn(Optional.of(s));

            controller.reconciliarEventosPendientes();

            verify(sucursalService, never()).bloquearSucursalesAdicionales(anyString());
            verify(emailService, never()).enviarEmailSuscripcion(any(), any(), any(), any());
            ArgumentCaptor<WebhookLog> capLog = ArgumentCaptor.forClass(WebhookLog.class);
            verify(webhookLogRepository).save(capLog.capture());
            assertThat(capLog.getValue().getEstado()).isEqualTo("PROCESADO");
        }

        @Test
        @DisplayName("charge.succeeded PROCESADO_PARCIAL — suscripción ya no está ACTIVA (ej. CANCELADA) → PROCESADO sin desbloquear")
        void parcialExitoso_susNoActiva_marcaProcesadoSinDesbloquear() {
            WebhookLog log = logParcial(EVT_SUCCESS, 0);
            Suscripcion s = suscripcionPro("CANCELADA");
            when(webhookLogRepository.findProcesadoParcialPendientes(anyInt(), any()))
                    .thenReturn(List.of(log));
            when(suscripcionRepository.findByPreapprovalId(SUB_ID)).thenReturn(Optional.of(s));

            controller.reconciliarEventosPendientes();

            verify(sucursalService, never()).desbloquearSucursalesConLimite(anyString(), anyInt());
            verify(emailService, never()).enviarEmailSuscripcion(any(), any(), any(), any());
            ArgumentCaptor<WebhookLog> capLog = ArgumentCaptor.forClass(WebhookLog.class);
            verify(webhookLogRepository).save(capLog.capture());
            assertThat(capLog.getValue().getEstado()).isEqualTo("PROCESADO");
        }

        @Test
        @DisplayName("PROCESADO_PARCIAL con intentos >= MAX → PENDIENTE_REVISION sin intentar branch op")
        void parcial_agotaIntentos_marcaPendienteRevision() {
            // intentos = MAX_INTENTOS_RECONCILIACION - 1, tras incremento llega al límite
            int intentosActuales = CulqiWebhookController.MAX_INTENTOS_RECONCILIACION - 1;
            WebhookLog log = logParcial(EVT_FAILED, intentosActuales);
            when(webhookLogRepository.findProcesadoParcialPendientes(anyInt(), any()))
                    .thenReturn(List.of(log));

            controller.reconciliarEventosPendientes();

            verify(sucursalService, never()).bloquearSucursalesAdicionales(anyString());
            ArgumentCaptor<WebhookLog> capLog = ArgumentCaptor.forClass(WebhookLog.class);
            verify(webhookLogRepository).save(capLog.capture());
            assertThat(capLog.getValue().getEstado()).isEqualTo("PENDIENTE_REVISION");
            assertThat(capLog.getValue().getIntentos())
                    .isEqualTo(CulqiWebhookController.MAX_INTENTOS_RECONCILIACION);
        }

        @Test
        @DisplayName("R-A1 — branch op falla DURANTE el reintento → mantiene PROCESADO_PARCIAL (no ERROR)")
        void parcialFallido_segundoFallo_mantieneProcesadoParcial() {
            // Primer reintento del scheduler: intentos=0 → tras incremento será 1 (< MAX=3).
            // La branch op vuelve a lanzar excepción. El fix R-A1 mantiene PROCESADO_PARCIAL
            // para que el scheduler lo reintente en el siguiente ciclo.
            WebhookLog log = logParcial(EVT_FAILED, 0);
            Suscripcion s = suscripcionPro("SUSPENDIDA");
            when(webhookLogRepository.findProcesadoParcialPendientes(anyInt(), any()))
                    .thenReturn(List.of(log));
            when(suscripcionRepository.findByPreapprovalId(SUB_ID)).thenReturn(Optional.of(s));
            when(sucursalService.bloquearSucursalesAdicionales(TENANT))
                    .thenThrow(new RuntimeException("DB timeout en el reintento"));

            controller.reconciliarEventosPendientes();

            ArgumentCaptor<WebhookLog> capLog = ArgumentCaptor.forClass(WebhookLog.class);
            verify(webhookLogRepository).save(capLog.capture());
            // Debe mantener PROCESADO_PARCIAL, no ERROR ni PROCESADO
            assertThat(capLog.getValue().getEstado()).isEqualTo("PROCESADO_PARCIAL");
            assertThat(capLog.getValue().getIntentos()).isEqualTo(1);
        }

        @Test
        @DisplayName("R-A2 — plan cambió a BÁSICO entre el evento original y el reintento → PROCESADO sin bloquear sucursales")
        void parcialFallido_planCambiaABasico_marcaProcesadoSinBloquear() {
            // El tenant hizo downgrade PRO→BÁSICO después del evento PROCESADO_PARCIAL.
            // El downgrade ya llamó a bloquearSucursalesAdicionales() por su propio flujo
            // (CulqiController.java:584). El reconciliador encuentra planId="BASICO" y
            // no repite la operación de bloqueo.
            WebhookLog log = logParcial(EVT_FAILED, 0);
            Suscripcion s = Suscripcion.builder()
                    .id(5L).tenantId(TENANT).planId("BASICO")
                    .preapprovalId(SUB_ID).estado("SUSPENDIDA")
                    .precioMensual(new BigDecimal("89.00"))
                    .build();
            when(webhookLogRepository.findProcesadoParcialPendientes(anyInt(), any()))
                    .thenReturn(List.of(log));
            when(suscripcionRepository.findByPreapprovalId(SUB_ID)).thenReturn(Optional.of(s));

            controller.reconciliarEventosPendientes();

            // El downgrade ya bloqueó las sucursales — reconciliador no repite el bloqueo
            verify(sucursalService, never()).bloquearSucursalesAdicionales(anyString());
            verify(emailService, never()).enviarEmailSuscripcion(any(), any(), any(), any());
            ArgumentCaptor<WebhookLog> capLog = ArgumentCaptor.forClass(WebhookLog.class);
            verify(webhookLogRepository).save(capLog.capture());
            assertThat(capLog.getValue().getEstado()).isEqualTo("PROCESADO");
        }
    }

    // ── B — CAS: race condition de preapprovalId reemplazado ─────────────────

    @Nested
    @DisplayName("B — CAS: webhook de preapprovalId ya reemplazado → IGNORADO")
    class CasPreapprovalId {

        @Test
        @DisplayName("charge.failed con preapprovalId ya reemplazado (CAS retorna 0) → IGNORADO, sin email, sin bloqueo")
        void chargeFallido_casRetornaCero_ignorado_sinEmailNiBranch() {
            Suscripcion s = suscripcionPro("ACTIVA");
            when(suscripcionRepository.findByPreapprovalId(SUB_ID)).thenReturn(Optional.of(s));
            // Simula que entre la lectura y el UPDATE, upgradePro() reemplazó el preapprovalId
            when(suscripcionRepository.updateEstadoIfPreapprovalIdMatches(
                    eq(5L), eq("SUSPENDIDA"), eq(SUB_ID))).thenReturn(0);

            controller.recibirWebhook(webhookDTO(EVT_FAILED, SUB_ID));

            verify(emailService, never()).enviarEmailSuscripcion(any(), any(), any(), any());
            verify(sucursalService, never()).bloquearSucursalesAdicionales(anyString());
            ArgumentCaptor<WebhookLog> capLog = ArgumentCaptor.forClass(WebhookLog.class);
            verify(webhookLogRepository, atLeast(2)).save(capLog.capture());
            assertThat(capLog.getAllValues())
                    .anyMatch(l -> "IGNORADO".equals(l.getEstado()));
            assertThat(capLog.getAllValues())
                    .noneMatch(l -> "SUSPENDIDA".equals(l.getEstado()))
                    .noneMatch(l -> "PROCESADO".equals(l.getEstado()));
        }

        @Test
        @DisplayName("charge.succeeded con preapprovalId ya reemplazado (CAS retorna 0) → IGNORADO, sin email, sin desbloqueo")
        void chargeExitoso_casRetornaCero_ignorado_sinEmailNiBranch() {
            Suscripcion s = suscripcionPro("SUSPENDIDA");
            when(suscripcionRepository.findByPreapprovalId(SUB_ID)).thenReturn(Optional.of(s));
            when(suscripcionRepository.updateEstadoIfPreapprovalIdMatches(
                    eq(5L), eq("ACTIVA"), eq(SUB_ID))).thenReturn(0);

            controller.recibirWebhook(webhookDTO(EVT_SUCCESS, SUB_ID));

            verify(emailService, never()).enviarEmailSuscripcion(any(), any(), any(), any());
            verify(sucursalService, never()).desbloquearSucursalesConLimite(anyString(), anyInt());
            ArgumentCaptor<WebhookLog> capLog = ArgumentCaptor.forClass(WebhookLog.class);
            verify(webhookLogRepository, atLeast(2)).save(capLog.capture());
            assertThat(capLog.getAllValues())
                    .anyMatch(l -> "IGNORADO".equals(l.getEstado()));
        }

        @Test
        @DisplayName("charge.failed con preapprovalId vigente (CAS retorna 1) → flujo normal: suspende y bloquea")
        void chargeFallido_casRetornaUno_flujoNormal() {
            Suscripcion s = suscripcionPro("ACTIVA");
            when(suscripcionRepository.findByPreapprovalId(SUB_ID)).thenReturn(Optional.of(s));
            when(suscripcionRepository.updateEstadoIfPreapprovalIdMatches(
                    eq(5L), eq("SUSPENDIDA"), eq(SUB_ID))).thenReturn(1);
            when(sucursalService.bloquearSucursalesAdicionales(TENANT)).thenReturn(2);

            controller.recibirWebhook(webhookDTO(EVT_FAILED, SUB_ID));

            verify(emailService).enviarEmailSuscripcion(any(), any(), eq("SUSPENDIDA"), any());
            verify(sucursalService).bloquearSucursalesAdicionales(TENANT);
            ArgumentCaptor<WebhookLog> capLog = ArgumentCaptor.forClass(WebhookLog.class);
            verify(webhookLogRepository, atLeast(2)).save(capLog.capture());
            assertThat(capLog.getAllValues())
                    .anyMatch(l -> "PROCESADO".equals(l.getEstado()));
        }

        @Test
        @DisplayName("R-B1 — charge.failed correlacionado solo por email (sin subscription_id ni chargeId ni customer_id) → PENDIENTE_REVISION")
        void chargeFallido_soloEmail_sinIdentificadoresVerificables_marcaPendienteRevision() {
            // Payload con solo email: correlación más débil. Sin subscription_id (no CAS),
            // sin chargeId (no verificación Culqi), sin customer_id (sin garantía de unidad).
            // Un evento tardío podría suspender erróneamente. Guard R-B1 → PENDIENTE_REVISION.
            Suscripcion s = suscripcionPro("ACTIVA");
            when(suscripcionRepository.findByPreapprovalId(any())).thenReturn(Optional.empty());
            when(suscripcionRepository.findAllByCulqiCustomerIdOrderByIdDesc(any())).thenReturn(List.of());
            // Encontrada por email (estrategia 3)
            when(usuarioRepository.findByEmail("admin@vet-fonavi.com"))
                    .thenReturn(Optional.of(usuarioPrincipal));
            when(suscripcionRepository.findAllByUsuarioPrincipalId(1L)).thenReturn(List.of(s));

            com.stockflow.dto.CulqiWebhookDTO dto = new com.stockflow.dto.CulqiWebhookDTO();
            dto.setId("evt_email_only_" + System.nanoTime());
            dto.setType(EVT_FAILED);
            dto.setData("{\"email\":\"admin@vet-fonavi.com\"}"); // solo email, sin los demás identificadores
            controller.recibirWebhook(dto);

            // No debe invocar CAS
            verify(suscripcionRepository, never())
                    .updateEstadoIfPreapprovalIdMatches(any(), any(), any());
            // No debe enviar email ni bloquear sucursales
            verify(emailService, never()).enviarEmailSuscripcion(any(), any(), any(), any());
            verify(sucursalService, never()).bloquearSucursalesAdicionales(anyString());
            // Estado del log: PENDIENTE_REVISION (no SUSPENDIDA, no PROCESADO)
            ArgumentCaptor<WebhookLog> capLog = ArgumentCaptor.forClass(WebhookLog.class);
            verify(webhookLogRepository, atLeast(2)).save(capLog.capture());
            assertThat(capLog.getAllValues())
                    .anyMatch(l -> "PENDIENTE_REVISION".equals(l.getEstado()));
            assertThat(capLog.getAllValues())
                    .noneMatch(l -> "PROCESADO".equals(l.getEstado()));
        }

        @Test
        @DisplayName("R-B1 — charge.failed sin subscription_id PERO con chargeId verificado → flujo normal")
        void chargeFallido_sinSubscriptionId_conChargeId_verificaConCulqi_flujoNormal() {
            // Tiene id chr_xxx (chargeId) pero no subscription_id.
            // Culqi devuelve el cargo con subscription_id que coincide con el preapprovalId
            // local → verificación exitosa → flujo normal continúa (R-B1 guard no aplica).
            Suscripcion s = suscripcionPro("ACTIVA");
            when(suscripcionRepository.findByPreapprovalId(SUB_ID)).thenReturn(Optional.empty());
            when(suscripcionRepository.findAllByCulqiCustomerIdOrderByIdDesc("cus_test_002"))
                    .thenReturn(List.of(s));
            when(culqiService.obtenerCargo("chr_legacy_001"))
                    .thenReturn(Map.of("id", "chr_legacy_001", "subscription_id", SUB_ID));
            when(sucursalService.bloquearSucursalesAdicionales(TENANT)).thenReturn(1);

            com.stockflow.dto.CulqiWebhookDTO dto = new com.stockflow.dto.CulqiWebhookDTO();
            dto.setId("evt_legacy_chr_" + System.nanoTime());
            dto.setType(EVT_FAILED);
            // id empieza con "chr_" → extraerChargeId lo detecta; no subscription_id
            dto.setData("{\"id\":\"chr_legacy_001\",\"customer_id\":\"cus_test_002\"}");
            controller.recibirWebhook(dto);

            // Con chargeId verificado en Culqi, el flujo normal continúa
            verify(emailService).enviarEmailSuscripcion(any(), any(), eq("SUSPENDIDA"), any());
            verify(sucursalService).bloquearSucursalesAdicionales(TENANT);
        }
    }
}
