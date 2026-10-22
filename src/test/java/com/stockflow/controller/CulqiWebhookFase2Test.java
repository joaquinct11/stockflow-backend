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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Fase 2: bloqueo/desbloqueo de sucursales por pagos fallidos y recuperación.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Fase 2 — CulqiWebhookController: bloqueo/desbloqueo de sucursales por impago")
class CulqiWebhookFase2Test {

    private static final String TENANT      = "vet-fonavi";
    private static final String SUB_ID      = "sxn_pro_123";
    private static final String EVT_FAILED  = "subscription.charge.failed";
    private static final String EVT_SUCCESS = "subscription.charge.succeeded";

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
        // B — CAS por defecto: preapprovalId vigente (1 = actualizado). Tests de race sobreescriben con 0.
        when(suscripcionRepository.updateEstadoIfPreapprovalIdMatches(any(), any(), any())).thenReturn(1);
    }

    private Suscripcion suscripcionPro(String estado) {
        return Suscripcion.builder()
                .id(5L).tenantId(TENANT).planId("PRO")
                .preapprovalId(SUB_ID).estado(estado)
                .precioMensual(new BigDecimal("199.00"))
                .fechaInicio(LocalDateTime.now().minusMonths(1))
                .currentPeriodStart(LocalDateTime.now().minusMonths(1))
                .currentPeriodEnd(LocalDateTime.now().plusMonths(1))
                .build();
    }

    private Suscripcion suscripcionBasico(String estado) {
        return Suscripcion.builder()
                .id(6L).tenantId(TENANT).planId("BASICO")
                .preapprovalId(SUB_ID).estado(estado)
                .precioMensual(new BigDecimal("89.00"))
                .build();
    }

    private com.stockflow.dto.CulqiWebhookDTO webhookDTO(String type, String subscriptionId) {
        com.stockflow.dto.CulqiWebhookDTO dto = new com.stockflow.dto.CulqiWebhookDTO();
        dto.setId("evt_" + System.nanoTime());
        dto.setType(type);
        dto.setData("{\"subscription_id\":\"" + subscriptionId + "\"}");
        return dto;
    }

    // ── A: Pago fallido — bloqueo de sucursales ──────────────────────────────

    @Nested
    @DisplayName("A — charge.failed: bloqueo de sucursales en plan PRO")
    class ChargeFallidoPro {

        @Test
        @DisplayName("pago fallido PRO → bloquearSucursalesAdicionales() invocado y suscripción → SUSPENDIDA")
        void fallidoPro_bloqueaSucursalesYSuspende() {
            Suscripcion s = suscripcionPro("ACTIVA");
            when(suscripcionRepository.findByPreapprovalId(SUB_ID)).thenReturn(Optional.of(s));
            when(sucursalService.bloquearSucursalesAdicionales(TENANT)).thenReturn(2);

            controller.recibirWebhook(webhookDTO(EVT_FAILED, SUB_ID));

            verify(sucursalService).bloquearSucursalesAdicionales(TENANT);
            ArgumentCaptor<Suscripcion> cap = ArgumentCaptor.forClass(Suscripcion.class);
            verify(suscripcionRepository).save(cap.capture());
            assertThat(cap.getValue().getEstado()).isEqualTo("SUSPENDIDA");
        }

        @Test
        @DisplayName("pago fallido BÁSICO → bloquearSucursalesAdicionales() NO invocado")
        void fallidoBasico_noBloqueaSucursales() {
            Suscripcion s = suscripcionBasico("ACTIVA");
            when(suscripcionRepository.findByPreapprovalId(SUB_ID)).thenReturn(Optional.of(s));

            controller.recibirWebhook(webhookDTO(EVT_FAILED, SUB_ID));

            verify(sucursalService, never()).bloquearSucursalesAdicionales(anyString());
        }

        @Test
        @DisplayName("pago fallido con suscripción ya SUSPENDIDA → idempotente, no re-bloquea ni re-emailea")
        void fallidoYaSuspendida_idempotente() {
            Suscripcion s = suscripcionPro("SUSPENDIDA");
            when(suscripcionRepository.findByPreapprovalId(SUB_ID)).thenReturn(Optional.of(s));

            controller.recibirWebhook(webhookDTO(EVT_FAILED, SUB_ID));

            verify(sucursalService, never()).bloquearSucursalesAdicionales(anyString());
            verify(emailService, never()).enviarEmailSuscripcion(any(), any(), any(), any());
        }

        @Test
        @DisplayName("pago fallido con suscripción CANCELADA → ignorado, no bloquea")
        void fallidoCancelada_ignorado() {
            Suscripcion s = suscripcionPro("CANCELADA");
            when(suscripcionRepository.findByPreapprovalId(SUB_ID)).thenReturn(Optional.of(s));

            controller.recibirWebhook(webhookDTO(EVT_FAILED, SUB_ID));

            verify(sucursalService, never()).bloquearSucursalesAdicionales(anyString());
        }
    }

    // ── E.1: Guard contra eventos tardíos en reconciliación ──────────────────

    @Nested
    @DisplayName("E.1 — charge.failed reconciliado con suscripción ya ACTIVA → PENDIENTE_REVISION")
    class GuardE1Reconciliacion {

        @Test
        @DisplayName("charge.failed con intentos=1 y suscripción ACTIVA → PENDIENTE_REVISION, no bloquea")
        void reconciliado_activaYaRenovada_pendienteRevision() {
            // Un evento charge.failed que quedó SIN_CORRELACION es reintentado por reconciliarEventosPendientes().
            // Para entonces la suscripción ya está ACTIVA (charge.succeeded llegó después).
            // El guard E.1 debe marcarlo PENDIENTE_REVISION sin bloquear sucursales.
            WebhookLog logReconciliado = WebhookLog.builder()
                    .id(99L)
                    .webhookId("evt_stale_failed")
                    .tipo(EVT_FAILED)
                    .estado("SIN_CORRELACION")
                    .intentos(1)  // Ya lleva 1 reintento — viene de reconciliarEvento()
                    .detalles("{\"subscription_id\":\"" + SUB_ID + "\"}")
                    .fechaProcesamiento(LocalDateTime.now().minusHours(1))
                    .build();

            Suscripcion sActiva = suscripcionPro("ACTIVA");
            when(suscripcionRepository.findByPreapprovalId(SUB_ID)).thenReturn(Optional.of(sActiva));
            when(webhookLogRepository.findSinCorrelacionPendientes(
                    eq(CulqiWebhookController.MAX_INTENTOS_RECONCILIACION), any(LocalDateTime.class)))
                    .thenReturn(List.of(logReconciliado));

            controller.reconciliarEventosPendientes();

            // Guard E.1: no bloquear sucursales
            verify(sucursalService, never()).bloquearSucursalesAdicionales(anyString());
            // Log debe quedar en PENDIENTE_REVISION
            ArgumentCaptor<WebhookLog> capLog = ArgumentCaptor.forClass(WebhookLog.class);
            verify(webhookLogRepository).save(capLog.capture());
            assertThat(capLog.getValue().getEstado()).isEqualTo("PENDIENTE_REVISION");
        }

        @Test
        @DisplayName("charge.failed fresco (intentos=0) con suscripción ACTIVA → sí bloquea (flujo normal)")
        void fresco_intentosCero_activaInicial_bloqueaNormal() {
            // Confirmar que el guard E.1 no interfiere con el flujo normal (intentos=0).
            Suscripcion sActiva = suscripcionPro("ACTIVA");
            when(suscripcionRepository.findByPreapprovalId(SUB_ID)).thenReturn(Optional.of(sActiva));
            when(sucursalService.bloquearSucursalesAdicionales(TENANT)).thenReturn(1);

            controller.recibirWebhook(webhookDTO(EVT_FAILED, SUB_ID));

            // Con intentos=0, no se activa E.1 y el flujo normal bloquea
            verify(sucursalService).bloquearSucursalesAdicionales(TENANT);
        }
    }

    // ── B: Pago exitoso — desbloqueo de sucursales ───────────────────────────

    @Nested
    @DisplayName("B — charge.succeeded: desbloqueo de sucursales")
    class ChargeSucedido {

        @Test
        @DisplayName("pago exitoso PRO → desbloquearSucursalesConLimite() invocado con maxSucursales=5")
        void exitosoPro_desbloqueaSucursalesConLimite() {
            Suscripcion s = suscripcionPro("SUSPENDIDA");
            when(suscripcionRepository.findByPreapprovalId(SUB_ID)).thenReturn(Optional.of(s));
            when(planLimitService.getMaxSucursales(TENANT)).thenReturn(5);
            when(sucursalService.desbloquearSucursalesConLimite(TENANT, 5)).thenReturn(2);

            controller.recibirWebhook(webhookDTO(EVT_SUCCESS, SUB_ID));

            verify(planLimitService).getMaxSucursales(TENANT);
            verify(sucursalService).desbloquearSucursalesConLimite(TENANT, 5);
        }

        @Test
        @DisplayName("pago exitoso BÁSICO → desbloquearSucursalesConLimite() invocado con maxSucursales=1")
        void exitosoBasico_desbloqueaConLimiteBasico() {
            Suscripcion s = suscripcionBasico("SUSPENDIDA");
            when(suscripcionRepository.findByPreapprovalId(SUB_ID)).thenReturn(Optional.of(s));
            when(planLimitService.getMaxSucursales(TENANT)).thenReturn(1);
            when(sucursalService.desbloquearSucursalesConLimite(TENANT, 1)).thenReturn(0);

            controller.recibirWebhook(webhookDTO(EVT_SUCCESS, SUB_ID));

            verify(sucursalService).desbloquearSucursalesConLimite(TENANT, 1);
        }

        @Test
        @DisplayName("pago exitoso suscripción CANCELADA → ignorado, no desbloquea")
        void exitosoCancelada_ignorado() {
            Suscripcion s = suscripcionPro("CANCELADA");
            when(suscripcionRepository.findByPreapprovalId(SUB_ID)).thenReturn(Optional.of(s));

            controller.recibirWebhook(webhookDTO(EVT_SUCCESS, SUB_ID));

            verify(sucursalService, never()).desbloquearSucursalesConLimite(anyString(), anyInt());
        }

        @Test
        @DisplayName("pago exitoso → suscripción queda ACTIVA")
        void exitoso_suscripcionActiva() {
            Suscripcion s = suscripcionPro("SUSPENDIDA");
            when(suscripcionRepository.findByPreapprovalId(SUB_ID)).thenReturn(Optional.of(s));
            when(planLimitService.getMaxSucursales(TENANT)).thenReturn(5);

            controller.recibirWebhook(webhookDTO(EVT_SUCCESS, SUB_ID));

            ArgumentCaptor<Suscripcion> cap = ArgumentCaptor.forClass(Suscripcion.class);
            verify(suscripcionRepository).save(cap.capture());
            assertThat(cap.getValue().getEstado()).isEqualTo("ACTIVA");
        }
    }
}
