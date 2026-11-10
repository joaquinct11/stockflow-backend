package com.stockflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.dto.CulqiWebhookDTO;
import com.stockflow.entity.Suscripcion;
import com.stockflow.entity.Usuario;
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

import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CulqiWebhookController — resolución de suscripción multi-tenant")
class CulqiWebhookControllerTest {

    private static final String EVENT_CHARGE_OK  = "charge.creation.succeeded";
    private static final String PREAPPROVAL_ID   = "sxn_test_botica_123";
    private static final String OTRO_PREAPPROVAL = "sxn_test_vet_456";
    private static final String EMAIL            = "joaquin@test.com";
    private static final Long   USUARIO_ID       = 5L;

    @Mock private SuscripcionRepository suscripcionRepository;
    @Mock private UsuarioRepository     usuarioRepository;
    @Mock private WebhookLogRepository  webhookLogRepository;
    @Mock private EmailService          emailService;
    @Mock private SucursalService       sucursalService;
    @Mock private CulqiService          culqiService;
    @Mock private PlanLimitService      planLimitService;
    @Spy  private ObjectMapper          objectMapper = new ObjectMapper();

    @InjectMocks private CulqiWebhookController controller;

    private Usuario       usuario;
    private Suscripcion   susBotica;
    private Suscripcion   susVet;
    private WebhookLog    webhookLogGuardado;

    @BeforeEach
    void setUp() {
        usuario = Usuario.builder()
                .id(USUARIO_ID)
                .email(EMAIL)
                .nombre("Joaquín")
                .activo(true)
                .build();

        susBotica = buildSuscripcion(10L, "botica-santa-fe", PREAPPROVAL_ID, "ACTIVA");
        susVet    = buildSuscripcion(15L, "veterinaria-vet",  OTRO_PREAPPROVAL, "ACTIVA");

        webhookLogGuardado = WebhookLog.builder()
                .id(1L).webhookId("evt_test_1").tipo(EVENT_CHARGE_OK)
                .estado("PROCESANDO").fechaProcesamiento(LocalDateTime.now()).build();

        // Defaults globales
        when(webhookLogRepository.existsByWebhookIdAndEstado(any(), eq("PROCESADO"))).thenReturn(false);
        when(webhookLogRepository.save(any())).thenReturn(webhookLogGuardado);
        when(suscripcionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sucursalService.inicializarPrincipal(any())).thenReturn(null);

        // Verificaciones API: defaults permisivos para no romper tests existentes.
        // obtenerCargo devuelve mapa vacío (cargo existe pero sin subscription_id que verificar).
        // obtenerSuscripcion devuelve null (suscripción no encontrada en Culqi → proceder con cancelación).
        when(culqiService.obtenerCargo(any())).thenReturn(new HashMap<>());
        when(culqiService.obtenerSuscripcion(any())).thenReturn(null);

        // B — CAS por defecto: simula que el preapprovalId sigue vigente (1 = actualizado).
        // Tests específicos de race condition sobreescriben este stub con 0.
        when(suscripcionRepository.updateEstadoIfPreapprovalIdMatches(any(), any(), any())).thenReturn(1);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Prioridad principal: subscription_id (debe seguir intacta)
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Resolución por subscription_id (comportamiento existente intacto)")
    class PorSubscriptionId {

        @Test
        @DisplayName("encuentra la suscripción correcta y NO consulta el email")
        void buscarSuscripcion_conSubscriptionId_encuentraCorrectamente() {
            when(suscripcionRepository.findByPreapprovalId(PREAPPROVAL_ID))
                    .thenReturn(Optional.of(susBotica));

            enviarWebhook(EVENT_CHARGE_OK,
                    "{\"id\":\"chr_test_001\",\"subscription_id\":\"" + PREAPPROVAL_ID + "\"}");

            verify(suscripcionRepository).findByPreapprovalId(PREAPPROVAL_ID);
            // Email NO debe usarse cuando hay subscription_id
            verify(usuarioRepository, never()).findByEmail(any());
        }

        @Test
        @DisplayName("charge.succeeded con subscription_id actualiza estado y fechaProximoCobro")
        void handleChargeSucedido_conSubscriptionId_actualizaLaSuscripcionCorrecta() {
            when(suscripcionRepository.findByPreapprovalId(PREAPPROVAL_ID))
                    .thenReturn(Optional.of(susBotica));

            enviarWebhook(EVENT_CHARGE_OK,
                    "{\"id\":\"chr_test_001\",\"subscription_id\":\"" + PREAPPROVAL_ID + "\"}");

            ArgumentCaptor<Suscripcion> captor = ArgumentCaptor.forClass(Suscripcion.class);
            verify(suscripcionRepository).save(captor.capture());

            Suscripcion guardada = captor.getValue();
            assertThat(guardada.getEstado()).isEqualTo("ACTIVA");
            assertThat(guardada.getFechaProximoCobro()).isAfter(LocalDateTime.now().minusSeconds(5));
            assertThat(guardada.getId()).isEqualTo(10L);  // se actualizó la de botica, no la de vet
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Fallback por email — nuevo comportamiento multi-tenant
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Fallback por email — nuevo comportamiento multi-tenant")
    class PorEmail {

        @Test
        @DisplayName("usuario con UNA sola suscripción → la encuentra y actualiza")
        void buscarPorEmail_usuarioConUnaSubscripcion_encuentraCorrectamente() {
            when(suscripcionRepository.findByPreapprovalId(any())).thenReturn(Optional.empty());
            when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));
            when(suscripcionRepository.findAllByUsuarioPrincipalId(USUARIO_ID))
                    .thenReturn(List.of(susBotica));

            enviarWebhook(EVENT_CHARGE_OK,
                    "{\"id\":\"chr_test_002\",\"email\":\"" + EMAIL + "\"}");

            verify(suscripcionRepository).findAllByUsuarioPrincipalId(USUARIO_ID);
            verify(suscripcionRepository).save(argThat(s -> s.getId().equals(10L)));
        }

        @Test
        @DisplayName("usuario con MÚLTIPLES suscripciones → no actualiza ninguna")
        void buscarPorEmail_usuarioConMultiplesSubscripciones_retornaEmpty() {
            when(suscripcionRepository.findByPreapprovalId(any())).thenReturn(Optional.empty());
            when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));
            when(suscripcionRepository.findAllByUsuarioPrincipalId(USUARIO_ID))
                    .thenReturn(List.of(susBotica, susVet));   // botica + veterinaria

            enviarWebhook(EVENT_CHARGE_OK,
                    "{\"id\":\"chr_test_003\",\"email\":\"" + EMAIL + "\"}");

            // No debe guardar ninguna suscripción
            verify(suscripcionRepository, never()).save(any(Suscripcion.class));
        }

        @Test
        @DisplayName("usuario sin suscripciones → no actualiza nada")
        void usuarioSinSuscripciones_retornaEmpty() {
            when(suscripcionRepository.findByPreapprovalId(any())).thenReturn(Optional.empty());
            when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));
            when(suscripcionRepository.findAllByUsuarioPrincipalId(USUARIO_ID))
                    .thenReturn(List.of());

            enviarWebhook(EVENT_CHARGE_OK,
                    "{\"id\":\"chr_test_004\",\"email\":\"" + EMAIL + "\"}");

            verify(suscripcionRepository, never()).save(any(Suscripcion.class));
        }

        @Test
        @DisplayName("no usa usuario.getTenantId() — el fallback legacy fue eliminado")
        void buscarPorEmail_noUsaTenantIdLegacy() {
            when(suscripcionRepository.findByPreapprovalId(any())).thenReturn(Optional.empty());
            when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));
            // Múltiples suscripciones — el viejo código usaría usuario.getTenantId() como fallback
            when(suscripcionRepository.findAllByUsuarioPrincipalId(USUARIO_ID))
                    .thenReturn(List.of(susBotica, susVet));

            enviarWebhook(EVENT_CHARGE_OK,
                    "{\"id\":\"chr_test_005\",\"email\":\"" + EMAIL + "\"}");

            // El fallback legacy llamaba findFirstByTenantIdOrderByIdDesc(usuario.getTenantId())
            // Si se eliminó correctamente, este método nunca debe ser invocado
            verify(suscripcionRepository, never())
                    .findFirstByTenantIdOrderByIdDesc(any());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Escenario completo multi-tenant
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Escenario multi-tenant completo")
    class MultiTenant {

        @Test
        @DisplayName("usuario con botica + veterinaria, webhook sin subscription_id → no actualiza ninguna suscripción")
        void usuarioConMultiplesTenants_sinSubscriptionId_noActualizaNinguna() {
            // Escenario real: Joaquín tiene 2 tenants suscritos
            // Culqi cobra y manda webhook con solo email (sin subscription_id)
            when(suscripcionRepository.findByPreapprovalId(any())).thenReturn(Optional.empty());
            when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));
            when(suscripcionRepository.findAllByUsuarioPrincipalId(USUARIO_ID))
                    .thenReturn(List.of(susBotica, susVet));

            enviarWebhook(EVENT_CHARGE_OK,
                    "{\"id\":\"chr_test_006\",\"email\":\"" + EMAIL + "\"}");

            // NINGUNA suscripción debe ser guardada — ambigüedad detectada
            verify(suscripcionRepository, never()).save(any(Suscripcion.class));
            // NINGÚN tenant debe ser identificado por la columna legacy
            verify(suscripcionRepository, never()).findFirstByTenantIdOrderByIdDesc(any());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // subscription.cancel.succeeded — formato real Culqi
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("subscription.cancel.succeeded — formato real Culqi")
    class CancelacionSuscripcion {

        private static final String EVENT_CANCEL = "subscription.cancel.succeeded";
        private static final String SUB_ID       = "sxn_test_W0aqhBP0SQaynxDs";
        private static final String PLAN_ID      = "pln_test_ECnPYT90vLp12ru1";

        /** Payload real observado en ngrok: subsId en message.object.subsId */
        private String payloadReal() {
            return "{\"message\":{\"object\":{\"subsId\":\"" + SUB_ID + "\",\"planId\":\"" + PLAN_ID + "\"}}," +
                   "\"action\":\"subscription.cancel.succeeded\"}";
        }

        /** Payload antiguo: id directamente como sxn_ */
        private String payloadLegacy() {
            return "{\"id\":\"" + SUB_ID + "\"}";
        }

        /** Suscripción con período expirado (currentPeriodEnd en el pasado) */
        private Suscripcion buildSuscripcionPeriodoExpirado(Long id, String tenantId, String preapprovalId) {
            return Suscripcion.builder()
                    .id(id)
                    .tenantId(tenantId)
                    .preapprovalId(preapprovalId)
                    .planId("BASICO")
                    .precioMensual(java.math.BigDecimal.valueOf(89))
                    .estado("ACTIVA")
                    .usuarioPrincipal(usuario)
                    .fechaProximoCobro(LocalDateTime.now().minusDays(1))
                    .currentPeriodStart(LocalDateTime.now().minusMonths(1))
                    .currentPeriodEnd(LocalDateTime.now().minusSeconds(1))
                    .build();
        }

        // ── Período activo: debe quedar en CANCELACION_PENDIENTE ─────────────

        @Test
        @DisplayName("formato real, período activo → CANCELACION_PENDIENTE (acceso conservado)")
        void formatoReal_periodoActivo_marcaCancelacionPendiente() {
            // buildSuscripcion crea con currentPeriodEnd = now().plusMonths(1) (futuro)
            Suscripcion sus = buildSuscripcion(20L, "tenant-cancel-1", SUB_ID, "ACTIVA");
            when(suscripcionRepository.findByPreapprovalId(SUB_ID)).thenReturn(Optional.of(sus));

            enviarWebhook(EVENT_CANCEL, payloadReal());

            ArgumentCaptor<Suscripcion> captor = ArgumentCaptor.forClass(Suscripcion.class);
            verify(suscripcionRepository).save(captor.capture());
            assertThat(captor.getValue().getEstado()).isEqualTo("CANCELACION_PENDIENTE");
            assertThat(captor.getValue().getFechaCancelacion()).isNotNull();
            // No debe enviar email de "CANCELADA" mientras el acceso siga activo
            verify(emailService, never()).enviarEmailSuscripcion(any(), any(), eq("CANCELADA"), any());
        }

        @Test
        @DisplayName("formato legado (data.id = sxn_), período activo → CANCELACION_PENDIENTE")
        void formatoLegado_periodoActivo_marcaCancelacionPendiente() {
            Suscripcion sus = buildSuscripcion(21L, "tenant-cancel-2", SUB_ID, "ACTIVA");
            when(suscripcionRepository.findByPreapprovalId(SUB_ID)).thenReturn(Optional.of(sus));

            enviarWebhook(EVENT_CANCEL, payloadLegacy());

            ArgumentCaptor<Suscripcion> captor = ArgumentCaptor.forClass(Suscripcion.class);
            verify(suscripcionRepository).save(captor.capture());
            assertThat(captor.getValue().getEstado()).isEqualTo("CANCELACION_PENDIENTE");
        }

        // ── Período expirado: debe quedar en CANCELADA inmediata ─────────────

        @Test
        @DisplayName("formato real, período expirado → CANCELADA inmediata")
        void formatoReal_periodoExpirado_marcaCancelada() {
            Suscripcion sus = buildSuscripcionPeriodoExpirado(25L, "tenant-cancel-5", SUB_ID);
            when(suscripcionRepository.findByPreapprovalId(SUB_ID)).thenReturn(Optional.of(sus));

            enviarWebhook(EVENT_CANCEL, payloadReal());

            ArgumentCaptor<Suscripcion> captor = ArgumentCaptor.forClass(Suscripcion.class);
            verify(suscripcionRepository).save(captor.capture());
            assertThat(captor.getValue().getEstado()).isEqualTo("CANCELADA");
            assertThat(captor.getValue().getFechaCancelacion()).isNotNull();
        }

        // ── CANCELACION_PENDIENTE: confirma sin cambiar estado ────────────────

        @Test
        @DisplayName("suscripción ya en CANCELACION_PENDIENTE → webhook PROCESADO, estado sin cambio")
        void suscripcionYaCancelacionPendiente_confirmaRemotaSinCambiarEstado() {
            // Escenario: usuario canceló desde el dashboard (→ CANCELACION_PENDIENTE),
            // luego Culqi confirma la cancelación con este webhook.
            Suscripcion sus = buildSuscripcion(26L, "tenant-cancel-6", SUB_ID, "CANCELACION_PENDIENTE");
            when(suscripcionRepository.findByPreapprovalId(SUB_ID)).thenReturn(Optional.of(sus));

            enviarWebhook(EVENT_CANCEL, payloadReal());

            // Estado no debe cambiar — la suscripción sigue en CANCELACION_PENDIENTE
            verify(suscripcionRepository, never()).save(any(Suscripcion.class));
            // No debe enviar email duplicado
            verify(emailService, never()).enviarEmailSuscripcion(any(), any(), any(), any());
        }

        // ── CANCELADA: ignorar completamente ─────────────────────────────────

        @Test
        @DisplayName("suscripción ya CANCELADA → IGNORADO, no envía email duplicado")
        void suscripcionYaCancelada_ignoraYNoEnviaEmail() {
            Suscripcion sus = buildSuscripcion(22L, "tenant-cancel-3", SUB_ID, "CANCELADA");
            when(suscripcionRepository.findByPreapprovalId(SUB_ID)).thenReturn(Optional.of(sus));

            enviarWebhook(EVENT_CANCEL, payloadReal());

            verify(suscripcionRepository, never()).save(any(Suscripcion.class));
            verify(emailService, never()).enviarEmailSuscripcion(any(), any(), any(), any());
        }

        // ── Casos comunes ─────────────────────────────────────────────────────

        @Test
        @DisplayName("suscripción desconocida (no existe en BD) → IGNORADO, no guarda")
        void suscripcionDesconocida_ignoraYNoPersiste() {
            when(suscripcionRepository.findByPreapprovalId(any())).thenReturn(Optional.empty());

            enviarWebhook(EVENT_CANCEL, payloadReal());

            verify(suscripcionRepository, never()).save(any(Suscripcion.class));
        }

        @Test
        @DisplayName("webhook duplicado (ya PROCESADO en WebhookLog) → idempotente, no guarda segunda vez")
        void webhookDuplicado_idempotente() {
            String eventId = "evt_test_cancel_dup_001";
            when(webhookLogRepository.existsByWebhookIdAndEstado(eventId, "PROCESADO")).thenReturn(true);

            CulqiWebhookDTO payload = new CulqiWebhookDTO();
            payload.setId(eventId);
            payload.setType(EVENT_CANCEL);
            payload.setData(payloadReal());
            controller.recibirWebhook(payload);

            // Ya estaba procesado → no debe buscar ni guardar suscripción
            verify(suscripcionRepository, never()).findByPreapprovalId(any());
            verify(suscripcionRepository, never()).save(any(Suscripcion.class));
        }

        @Test
        @DisplayName("correlaciona por preapprovalId exacto, no toca suscripciones de otros tenants")
        void correlacionaPorPreapprovalIdExacto() {
            String otroSubId = "sxn_test_OTRO_diferente";
            Suscripcion susCancelada  = buildSuscripcion(23L, "tenant-x", SUB_ID,    "ACTIVA");
            Suscripcion susIntacta    = buildSuscripcion(24L, "tenant-y", otroSubId, "ACTIVA");

            when(suscripcionRepository.findByPreapprovalId(SUB_ID)).thenReturn(Optional.of(susCancelada));
            when(suscripcionRepository.findByPreapprovalId(otroSubId)).thenReturn(Optional.of(susIntacta));

            enviarWebhook(EVENT_CANCEL, payloadReal());

            // Solo debe tocarse la de SUB_ID
            ArgumentCaptor<Suscripcion> captor = ArgumentCaptor.forClass(Suscripcion.class);
            verify(suscripcionRepository).save(captor.capture());
            assertThat(captor.getValue().getId()).isEqualTo(23L);
            assertThat(captor.getValue().getPreapprovalId()).isEqualTo(SUB_ID);
            // El estado correcto con período activo es CANCELACION_PENDIENTE
            assertThat(captor.getValue().getEstado()).isEqualTo("CANCELACION_PENDIENTE");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Guard anti-race: downgrade concurrente y webhook tardío
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Guard anti-race: downgrade/upgrade concurrente y webhook tardío")
    class DowngradeYWebhookTardio {

        private static final String EVENT_CANCEL  = "subscription.cancel.succeeded";
        private static final String SUB_PRO_ID    = "sxn_test_aj2qU29fHjFMUcTm";

        private String payloadConSubId(String subId) {
            return "{\"message\":{\"object\":{\"subsId\":\"" + subId + "\"}}}";
        }

        @Test
        @DisplayName("downgrade Pro→Básico: webhook tardío de cancelación de Pro llega DESPUÉS del downgrade → IGNORADO, no sobreescribe estado")
        void webhookTardio_preapprovalIdReemplazadoPorDowngrade_seIgnora() {
            // Escenario: downgrade completó y actualizó preapprovalId a sxn_BASICO.
            // El webhook de cancelación de sxn_PRO llega después.
            // 1ra llamada a findByPreapprovalId: carga inicial (la fila aún tenía sxn_PRO)
            // 2da llamada: guard detecta que ya no existe → IGNORADO
            Suscripcion susVieja = buildSuscripcion(27L, "vet-fonavi", SUB_PRO_ID, "ACTIVA");
            when(suscripcionRepository.findByPreapprovalId(SUB_PRO_ID))
                    .thenReturn(Optional.of(susVieja))  // carga inicial
                    .thenReturn(Optional.empty());       // guard: downgrade actualizó preapprovalId

            when(culqiService.obtenerSuscripcion(SUB_PRO_ID)).thenReturn(null);

            enviarWebhook(EVENT_CANCEL, payloadConSubId(SUB_PRO_ID));

            // El downgrade ya completó: NO debe guardarse ningún cambio de estado
            verify(suscripcionRepository, never()).save(any(Suscripcion.class));
            // No debe enviarse email de cancelación
            verify(emailService, never()).enviarEmailSuscripcion(any(), any(), any(), any());
        }

        @Test
        @DisplayName("cancelación real del usuario (sin downgrade concurrente) → CANCELACION_PENDIENTE (regresión del guard)")
        void cancelacionRealSinDowngrade_guardPasa_estadoCancelacionPendiente() {
            // Escenario normal: el preapprovalId no cambia entre la carga y el guard.
            // El guard debe pasar y la cancelación debe procesarse normalmente.
            String subId = "sxn_test_cancelacion_real_001";
            Suscripcion sus = buildSuscripcion(50L, "tenant-real", subId, "ACTIVA");

            // Ambas llamadas retornan la misma entidad con el mismo ID → guard pasa
            when(suscripcionRepository.findByPreapprovalId(subId)).thenReturn(Optional.of(sus));
            when(culqiService.obtenerSuscripcion(subId)).thenReturn(null);

            enviarWebhook(EVENT_CANCEL, payloadConSubId(subId));

            ArgumentCaptor<Suscripcion> captor = ArgumentCaptor.forClass(Suscripcion.class);
            verify(suscripcionRepository).save(captor.capture());
            assertThat(captor.getValue().getEstado()).isEqualTo("CANCELACION_PENDIENTE");
            assertThat(captor.getValue().getFechaCancelacion()).isNotNull();
        }

        @Test
        @DisplayName("webhook tardío: guard detecta que preapprovalId apunta a otra entidad → IGNORADO")
        void webhookTardio_preapprovalIdReasignadoAOtraEntidad_seIgnora() {
            // Caso extremo: la clave preapprovalId aparece en una fila diferente
            // entre la carga y el re-chequeo del guard.
            String subId = "sxn_test_reasignado_001";
            Suscripcion susVieja      = buildSuscripcion(60L, "tenant-a", subId, "ACTIVA");
            Suscripcion susReasignada = buildSuscripcion(61L, "tenant-b", subId, "ACTIVA");

            when(suscripcionRepository.findByPreapprovalId(subId))
                    .thenReturn(Optional.of(susVieja))       // carga inicial: fila 60
                    .thenReturn(Optional.of(susReasignada)); // guard: misma clave, fila 61

            when(culqiService.obtenerSuscripcion(subId)).thenReturn(null);

            enviarWebhook(EVENT_CANCEL, payloadConSubId(subId));

            // La correlación es ambigua o la fila fue reasignada: no debe modificarse nada
            verify(suscripcionRepository, never()).save(any(Suscripcion.class));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Cobros: charge.creation.succeeded y subscription.charge.succeeded
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Cobros — charge.succeeded (primer cobro y renovaciones)")
    class CargosSubscripcion {

        private static final String CHARGE_CREATION    = "charge.creation.succeeded";
        private static final String CHARGE_RENEWAL     = "subscription.charge.succeeded";
        private static final String CHARGE_FAILED      = "subscription.charge.failed";

        /** Payload de un cargo con subscription_id explícito */
        private String payloadConSubId(String subId) {
            return "{\"id\":\"chr_test_001\",\"subscription_id\":\"" + subId + "\"}";
        }

        // ── Cobro exitoso renueva período ─────────────────────────────────────

        @Test
        @DisplayName("cobro exitoso (renovación) actualiza fechaProximoCobro y mantiene ACTIVA")
        void chargeRenovacion_actualizaFechaProximoCobro() {
            when(suscripcionRepository.findByPreapprovalId(PREAPPROVAL_ID))
                    .thenReturn(Optional.of(susBotica));

            enviarWebhook(CHARGE_RENEWAL, payloadConSubId(PREAPPROVAL_ID));

            ArgumentCaptor<Suscripcion> captor = ArgumentCaptor.forClass(Suscripcion.class);
            verify(suscripcionRepository).save(captor.capture());
            Suscripcion guardada = captor.getValue();
            assertThat(guardada.getEstado()).isEqualTo("ACTIVA");
            assertThat(guardada.getFechaProximoCobro()).isAfter(LocalDateTime.now().plusDays(28));
        }

        @Test
        @DisplayName("cobro exitoso NO sobreescribe plan_codigo ni precio_mensual")
        void charge_noPisaPlanCodigoNiPrecio() {
            Suscripcion sus = Suscripcion.builder()
                    .id(30L).tenantId("vet-fonavi-27f0").preapprovalId(PREAPPROVAL_ID)
                    .planId("BASICO").planCodigo("VETERINARIA_BASICO")
                    .precioMensual(java.math.BigDecimal.valueOf(109))
                    .estado("ACTIVA").usuarioPrincipal(usuario)
                    .fechaProximoCobro(LocalDateTime.now().plusDays(5))
                    .currentPeriodStart(LocalDateTime.now().minusDays(25))
                    .currentPeriodEnd(LocalDateTime.now().plusDays(5))
                    .build();
            when(suscripcionRepository.findByPreapprovalId(PREAPPROVAL_ID)).thenReturn(Optional.of(sus));

            enviarWebhook(CHARGE_RENEWAL, payloadConSubId(PREAPPROVAL_ID));

            ArgumentCaptor<Suscripcion> captor = ArgumentCaptor.forClass(Suscripcion.class);
            verify(suscripcionRepository).save(captor.capture());
            assertThat(captor.getValue().getPlanCodigo()).isEqualTo("VETERINARIA_BASICO");
            assertThat(captor.getValue().getPrecioMensual()).isEqualByComparingTo("109");
        }

        // ── Guard: no reactivar CANCELACION_PENDIENTE ─────────────────────────

        @Test
        @DisplayName("charge.succeeded cuando CANCELACION_PENDIENTE → IGNORADO, no reactiva")
        void charge_cuandoCancelacionPendiente_noReactiva() {
            Suscripcion sus = buildSuscripcion(31L, "tenant-x", PREAPPROVAL_ID, "CANCELACION_PENDIENTE");
            when(suscripcionRepository.findByPreapprovalId(PREAPPROVAL_ID)).thenReturn(Optional.of(sus));

            enviarWebhook(CHARGE_RENEWAL, payloadConSubId(PREAPPROVAL_ID));

            // No debe guardar ni reactivar
            verify(suscripcionRepository, never()).save(any(Suscripcion.class));
            verify(emailService, never()).enviarEmailSuscripcion(any(), any(), any(), any());
        }

        @Test
        @DisplayName("charge.creation.succeeded cuando CANCELACION_PENDIENTE → IGNORADO, no reactiva")
        void chargeCreacion_cuandoCancelacionPendiente_noReactiva() {
            Suscripcion sus = buildSuscripcion(32L, "tenant-x", PREAPPROVAL_ID, "CANCELACION_PENDIENTE");
            when(suscripcionRepository.findByPreapprovalId(PREAPPROVAL_ID)).thenReturn(Optional.of(sus));

            enviarWebhook(CHARGE_CREATION, payloadConSubId(PREAPPROVAL_ID));

            verify(suscripcionRepository, never()).save(any(Suscripcion.class));
        }

        @Test
        @DisplayName("charge.succeeded cuando CANCELADA → IGNORADO")
        void charge_cuandoCancelada_seIgnora() {
            Suscripcion sus = buildSuscripcion(33L, "tenant-x", PREAPPROVAL_ID, "CANCELADA");
            when(suscripcionRepository.findByPreapprovalId(PREAPPROVAL_ID)).thenReturn(Optional.of(sus));

            enviarWebhook(CHARGE_RENEWAL, payloadConSubId(PREAPPROVAL_ID));

            verify(suscripcionRepository, never()).save(any(Suscripcion.class));
        }

        // ── Email: primer cobro vs renovación ────────────────────────────────

        @Test
        @DisplayName("charge.creation.succeeded envía email de confirmación del primer cobro")
        void chargeCreacion_enviaEmailConfirmacionPago() {
            when(suscripcionRepository.findByPreapprovalId(PREAPPROVAL_ID))
                    .thenReturn(Optional.of(susBotica));

            enviarWebhook(CHARGE_CREATION, payloadConSubId(PREAPPROVAL_ID));

            // El primer cobro confirmado cierra el ciclo iniciado por suscribir()
            verify(emailService).enviarEmailSuscripcion(
                    eq(usuario.getEmail()), eq(usuario.getNombre()), eq("ACTIVA"), any());
        }

        @Test
        @DisplayName("subscription.charge.succeeded (renovación) SÍ envía email de renovación")
        void chargeRenovacion_enviaEmailRenovacion() {
            when(suscripcionRepository.findByPreapprovalId(PREAPPROVAL_ID))
                    .thenReturn(Optional.of(susBotica));

            enviarWebhook(CHARGE_RENEWAL, payloadConSubId(PREAPPROVAL_ID));

            verify(emailService).enviarEmailSuscripcion(
                    eq(usuario.getEmail()), eq(usuario.getNombre()), eq("ACTIVA"), any());
        }

        // ── charge.failed ignora CANCELACION_PENDIENTE ───────────────────────

        @Test
        @DisplayName("charge.failed cuando CANCELACION_PENDIENTE → IGNORADO (cancelación voluntaria)")
        void chargeFailed_cuandoCancelacionPendiente_seIgnora() {
            Suscripcion sus = buildSuscripcion(34L, "tenant-x", PREAPPROVAL_ID, "CANCELACION_PENDIENTE");
            when(suscripcionRepository.findByPreapprovalId(PREAPPROVAL_ID)).thenReturn(Optional.of(sus));

            enviarWebhook(CHARGE_FAILED, payloadConSubId(PREAPPROVAL_ID));

            verify(suscripcionRepository, never()).save(any(Suscripcion.class));
            verify(emailService, never()).enviarEmailSuscripcion(any(), any(), eq("SUSPENDIDA"), any());
        }

        @Test
        @DisplayName("charge.failed en suscripción ACTIVA → SUSPENDIDA")
        void chargeFailed_activaMarcaSuspendida() {
            when(suscripcionRepository.findByPreapprovalId(PREAPPROVAL_ID))
                    .thenReturn(Optional.of(susBotica));

            enviarWebhook(CHARGE_FAILED, payloadConSubId(PREAPPROVAL_ID));

            ArgumentCaptor<Suscripcion> captor = ArgumentCaptor.forClass(Suscripcion.class);
            verify(suscripcionRepository).save(captor.capture());
            assertThat(captor.getValue().getEstado()).isEqualTo("SUSPENDIDA");
        }

        // ── Webhook duplicado ─────────────────────────────────────────────────

        @Test
        @DisplayName("webhook de cobro duplicado → idempotente, no actualiza segunda vez")
        void chargeWebhookDuplicado_idempotente() {
            String eventId = "evt_charge_dup_001";
            when(webhookLogRepository.existsByWebhookIdAndEstado(eventId, "PROCESADO")).thenReturn(true);

            CulqiWebhookDTO payload = new CulqiWebhookDTO();
            payload.setId(eventId);
            payload.setType(CHARGE_RENEWAL);
            payload.setData(payloadConSubId(PREAPPROVAL_ID));
            controller.recibirWebhook(payload);

            verify(suscripcionRepository, never()).findByPreapprovalId(any());
            verify(suscripcionRepository, never()).save(any(Suscripcion.class));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Correlación por customer_id — nueva estrategia 2
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Correlación por customer_id (nueva estrategia 2)")
    class PorCustomerId {

        private static final String CUSTOMER_ID      = "cus_test_botica_001";
        private static final String CHARGE_CREATION  = "charge.creation.succeeded";

        /** Payload de charge.creation.succeeded SIN subscription_id, CON customer_id */
        private String payloadSinSubIdConCustomerId() {
            return "{\"id\":\"chr_test_800\",\"customer_id\":\"" + CUSTOMER_ID + "\"}";
        }

        /** Payload sin subscription_id NI customer_id (solo email tenant-scoped) */
        private String payloadSoloEmail(String email) {
            return "{\"id\":\"chr_test_801\",\"email\":\"" + email + "\"}";
        }

        @Test
        @DisplayName("charge.creation.succeeded sin subscription_id pero con customer_id → correlaciona inequívocamente")
        void chargeCreacion_sinSubId_conCustomerId_correlaciona() {
            Suscripcion sus = buildSuscripcionConCustomerId(40L, "botica-abc", PREAPPROVAL_ID,
                    "ACTIVA", CUSTOMER_ID);
            when(suscripcionRepository.findByPreapprovalId(any())).thenReturn(Optional.empty());
            // Un solo resultado → correlación unívoca
            when(suscripcionRepository.findAllByCulqiCustomerIdOrderByIdDesc(CUSTOMER_ID))
                    .thenReturn(List.of(sus));

            enviarWebhook(CHARGE_CREATION, payloadSinSubIdConCustomerId());

            verify(suscripcionRepository).findAllByCulqiCustomerIdOrderByIdDesc(CUSTOMER_ID);
            ArgumentCaptor<Suscripcion> captor = ArgumentCaptor.forClass(Suscripcion.class);
            verify(suscripcionRepository).save(captor.capture());
            assertThat(captor.getValue().getId()).isEqualTo(40L);
            assertThat(captor.getValue().getEstado()).isEqualTo("ACTIVA");
        }

        @Test
        @DisplayName("charge.creation.succeeded con customer_id envía email de confirmación del primer cobro")
        void chargeCreacion_porCustomerId_enviaEmailConfirmacionPago() {
            Suscripcion sus = buildSuscripcionConCustomerId(41L, "botica-abc", PREAPPROVAL_ID,
                    "ACTIVA", CUSTOMER_ID);
            when(suscripcionRepository.findByPreapprovalId(any())).thenReturn(Optional.empty());
            when(suscripcionRepository.findAllByCulqiCustomerIdOrderByIdDesc(CUSTOMER_ID))
                    .thenReturn(List.of(sus));

            enviarWebhook(CHARGE_CREATION, payloadSinSubIdConCustomerId());

            verify(emailService).enviarEmailSuscripcion(
                    eq(usuario.getEmail()), eq(usuario.getNombre()), eq("ACTIVA"), any());
        }

        @Test
        @DisplayName("customer_id desconocido → cae al fallback de email (estrategia 3)")
        void customerIdDesconocido_caeAlFallbackDeEmail() {
            String emailTenantScoped = "joaquin+botica-abc@test.com";
            Suscripcion sus = buildSuscripcion(42L, "botica-abc", PREAPPROVAL_ID, "ACTIVA");
            when(suscripcionRepository.findByPreapprovalId(any())).thenReturn(Optional.empty());
            when(suscripcionRepository.findAllByCulqiCustomerIdOrderByIdDesc(CUSTOMER_ID))
                    .thenReturn(List.of());  // customer_id desconocido → vacío
            // Fallback: busca por email tenant-scoped que tiene tenantId extraíble
            when(suscripcionRepository.findFirstByTenantIdOrderByIdDesc("botica-abc"))
                    .thenReturn(Optional.of(sus));

            String payloadConEmailYCustomerId =
                    "{\"id\":\"chr_test_802\",\"customer_id\":\"" + CUSTOMER_ID + "\"," +
                    "\"email\":\"" + emailTenantScoped + "\"}";
            enviarWebhook(CHARGE_CREATION, payloadConEmailYCustomerId);

            // Debe haber intentado por customer_id y luego caer al email
            verify(suscripcionRepository).findAllByCulqiCustomerIdOrderByIdDesc(CUSTOMER_ID);
            verify(suscripcionRepository).findFirstByTenantIdOrderByIdDesc("botica-abc");
        }

        @Test
        @DisplayName("mismo customer_id en suscripción antigua y nueva — correlación ambigua → no modifica ninguna")
        void dosSubscripcionesParaMismoCustomer_correlacionAmbigua_noCorrompeLaActiva() {
            // Escenario: tenant canceló la suscripción antigua y creó una nueva, mismo cliente Culqi.
            // Llega un webhook tardío del primer cobro de la suscripción antigua.
            Suscripcion susAntigua = buildSuscripcionConCustomerId(44L, "botica-abc",
                    "sxn_test_ANTIGUO", "CANCELADA", CUSTOMER_ID);
            Suscripcion susNueva   = buildSuscripcionConCustomerId(45L, "botica-abc",
                    "sxn_test_NUEVA",   "ACTIVA",    CUSTOMER_ID);

            when(suscripcionRepository.findByPreapprovalId(any())).thenReturn(Optional.empty());
            // Hay DOS suscripciones para el mismo customer_id → correlación ambigua
            when(suscripcionRepository.findAllByCulqiCustomerIdOrderByIdDesc(CUSTOMER_ID))
                    .thenReturn(List.of(susNueva, susAntigua));  // ordenadas por id desc

            enviarWebhook(CHARGE_CREATION, payloadSinSubIdConCustomerId());

            // No debe modificar NINGUNA suscripción (ni la antigua ni la nueva)
            verify(suscripcionRepository, never()).save(any(Suscripcion.class));
            // No debe enviar email
            verify(emailService, never()).enviarEmailSuscripcion(any(), any(), any(), any());
        }

        @Test
        @DisplayName("customer_id resuelve ambigüedad multi-tenant sin round-trip a Culqi")
        void customerIdResuelveTenantSinAmbiguedad() {
            // Escenario: usuario con 2 tenants; el customer_id identifica unívocamente al tenant correcto
            Suscripcion susCorrecta = buildSuscripcionConCustomerId(43L, "botica-abc",
                    PREAPPROVAL_ID, "ACTIVA", CUSTOMER_ID);
            when(suscripcionRepository.findByPreapprovalId(any())).thenReturn(Optional.empty());
            when(suscripcionRepository.findAllByCulqiCustomerIdOrderByIdDesc(CUSTOMER_ID))
                    .thenReturn(List.of(susCorrecta));  // solo una → unívoco

            enviarWebhook(CHARGE_CREATION, payloadSinSubIdConCustomerId());

            // Debe verificar el cargo en Culqi (seguridad: confirmar que existe y no hay mismatch)
            verify(culqiService).obtenerCargo("chr_test_800");
            // No debe buscar por email (no es necesario)
            verify(usuarioRepository, never()).findByEmail(any());
            // Sí debe actualizar la suscripción correcta
            ArgumentCaptor<Suscripcion> captor = ArgumentCaptor.forClass(Suscripcion.class);
            verify(suscripcionRepository).save(captor.capture());
            assertThat(captor.getValue().getId()).isEqualTo(43L);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Primer cobro fallido — charge.creation.failed
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Primer cobro fallido — charge.creation.failed")
    class PrimerCobroFallido {

        private static final String CHARGE_CREATION_FAILED = "charge.creation.failed";
        private static final String CUSTOMER_ID_FAIL       = "cus_test_fail_botica";
        private static final String CHARGE_ID_FAIL         = "chr_test_FALLO_001";

        /** Payload real de charge.creation.failed: contiene "chargeId" (no el id del cobro directamente) */
        private String payloadConChargeId() {
            return "{\"chargeId\":\"" + CHARGE_ID_FAIL + "\"}";
        }

        /** Payload con customer_id directamente (si Culqi lo incluye en el failed) */
        private String payloadConCustomerId() {
            return "{\"customer_id\":\"" + CUSTOMER_ID_FAIL + "\"}";
        }

        @Test
        @DisplayName("charge.creation.failed con chargeId → obtenerCargo → customer_id → SUSPENDIDA")
        void primerCobro_chargeCreationFailed_porChargeId_marcaSuspendida() {
            // Simulación: Culqi envía payload con solo chargeId (formato real de charge.creation.failed)
            Map<String, Object> cargoCompleto = Map.of(
                    "id", CHARGE_ID_FAIL,
                    "customer_id", CUSTOMER_ID_FAIL
                    // Sin subscription_id ni email — solo customer_id
            );
            Suscripcion sus = buildSuscripcionConCustomerId(50L, "botica-abc", PREAPPROVAL_ID,
                    "ACTIVA", CUSTOMER_ID_FAIL);

            when(suscripcionRepository.findByPreapprovalId(any())).thenReturn(Optional.empty());
            // Estrategia 2: payload no tiene customer_id → findAll devuelve vacío para cualquier valor
            when(suscripcionRepository.findAllByCulqiCustomerIdOrderByIdDesc(any())).thenReturn(List.of());
            when(culqiService.obtenerCargo(CHARGE_ID_FAIL)).thenReturn(cargoCompleto);
            // Estrategia 4: customer_id extraído del cargo → un resultado → unívoco
            when(suscripcionRepository.findAllByCulqiCustomerIdOrderByIdDesc(CUSTOMER_ID_FAIL))
                    .thenReturn(List.of(sus));

            enviarWebhook(CHARGE_CREATION_FAILED, payloadConChargeId());

            // Debe marcar SUSPENDIDA
            ArgumentCaptor<Suscripcion> captor = ArgumentCaptor.forClass(Suscripcion.class);
            verify(suscripcionRepository).save(captor.capture());
            assertThat(captor.getValue().getEstado()).isEqualTo("SUSPENDIDA");
            // Debe notificar al usuario del fallo
            verify(emailService).enviarEmailSuscripcion(
                    eq(usuario.getEmail()), eq(usuario.getNombre()), eq("SUSPENDIDA"), any());
        }

        @Test
        @DisplayName("charge.creation.failed con customer_id en payload → estrategia 2 → SUSPENDIDA")
        void primerCobro_chargeCreationFailed_porCustomerIdDirecto_marcaSuspendida() {
            // Caso en que Culqi incluye customer_id directamente en el payload del failed
            Suscripcion sus = buildSuscripcionConCustomerId(51L, "botica-abc", PREAPPROVAL_ID,
                    "ACTIVA", CUSTOMER_ID_FAIL);

            when(suscripcionRepository.findByPreapprovalId(any())).thenReturn(Optional.empty());
            when(suscripcionRepository.findAllByCulqiCustomerIdOrderByIdDesc(CUSTOMER_ID_FAIL))
                    .thenReturn(List.of(sus));  // un resultado → correlación unívoca

            enviarWebhook(CHARGE_CREATION_FAILED, payloadConCustomerId());

            ArgumentCaptor<Suscripcion> captor = ArgumentCaptor.forClass(Suscripcion.class);
            verify(suscripcionRepository).save(captor.capture());
            assertThat(captor.getValue().getEstado()).isEqualTo("SUSPENDIDA");
            verify(emailService).enviarEmailSuscripcion(
                    eq(usuario.getEmail()), eq(usuario.getNombre()), eq("SUSPENDIDA"), any());
        }

        @Test
        @DisplayName("charge.creation.failed no correlacionado → SIN_CORRELACION en WebhookLog, no toca suscripción")
        void primerCobro_chargeCreationFailed_sinCorrelacion_marcaSinCorrelacion() {
            // Escenario: webhook llega pero no se puede correlacionar (ej. culqiCustomerId no persistido)
            // La suscripción permanece intacta — no hay acción sin correlación segura
            when(suscripcionRepository.findByPreapprovalId(any())).thenReturn(Optional.empty());
            when(suscripcionRepository.findAllByCulqiCustomerIdOrderByIdDesc(any())).thenReturn(List.of());
            when(culqiService.obtenerCargo(any())).thenReturn(null);

            enviarWebhook(CHARGE_CREATION_FAILED, payloadConChargeId());

            // No debe guardar ni enviar email — ningún tenant incorrecto suspendido
            verify(suscripcionRepository, never()).save(any(Suscripcion.class));
            verify(emailService, never()).enviarEmailSuscripcion(any(), any(), any(), any());
            // El WebhookLog debe quedar SIN_CORRELACION (no PROCESADO ni IGNORADO)
            // para que Culqi pueda reintentarlo y el operador pueda conciliarlo manualmente
            ArgumentCaptor<WebhookLog> logCaptor = ArgumentCaptor.forClass(WebhookLog.class);
            verify(webhookLogRepository, atLeastOnce()).save(logCaptor.capture());
            assertThat(logCaptor.getAllValues())
                    .anyMatch(l -> "SIN_CORRELACION".equals(l.getEstado()));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Eventos sin correlación → WebhookLog SIN_CORRELACION (permite reintento)
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Eventos sin correlación — estado SIN_CORRELACION en WebhookLog")
    class SinCorrelacion {

        @Test
        @DisplayName("charge.creation.succeeded no correlacionado → SIN_CORRELACION, permite reintento de Culqi")
        void chargeSucceeded_sinCorrelacion_noEsProcessado_permitReintento() {
            // Sin subscription_id, sin customer_id, sin email → nada correlaciona
            when(suscripcionRepository.findByPreapprovalId(any())).thenReturn(Optional.empty());
            when(suscripcionRepository.findAllByCulqiCustomerIdOrderByIdDesc(any())).thenReturn(List.of());

            enviarWebhook("charge.creation.succeeded", "{\"id\":\"chr_test_unknown\"}");

            // No modifica ninguna suscripción
            verify(suscripcionRepository, never()).save(any(Suscripcion.class));
            // No envía email
            verify(emailService, never()).enviarEmailSuscripcion(any(), any(), any(), any());
            // El WebhookLog debe quedar SIN_CORRELACION — NO PROCESADO (Culqi puede reintentar)
            ArgumentCaptor<WebhookLog> logCaptor = ArgumentCaptor.forClass(WebhookLog.class);
            verify(webhookLogRepository, atLeastOnce()).save(logCaptor.capture());
            assertThat(logCaptor.getAllValues())
                    .anyMatch(l -> "SIN_CORRELACION".equals(l.getEstado()));
            assertThat(logCaptor.getAllValues())
                    .noneMatch(l -> "PROCESADO".equals(l.getEstado()));
        }

        @Test
        @DisplayName("charge.creation.failed no correlacionado → SIN_CORRELACION (mismo contrato)")
        void chargeFailed_sinCorrelacion_noEsProcessado() {
            when(suscripcionRepository.findByPreapprovalId(any())).thenReturn(Optional.empty());
            when(suscripcionRepository.findAllByCulqiCustomerIdOrderByIdDesc(any())).thenReturn(List.of());
            when(culqiService.obtenerCargo(any())).thenReturn(null);

            enviarWebhook("charge.creation.failed", "{\"chargeId\":\"chr_test_UNK\"}");

            verify(suscripcionRepository, never()).save(any(Suscripcion.class));
            ArgumentCaptor<WebhookLog> logCaptor = ArgumentCaptor.forClass(WebhookLog.class);
            verify(webhookLogRepository, atLeastOnce()).save(logCaptor.capture());
            assertThat(logCaptor.getAllValues())
                    .anyMatch(l -> "SIN_CORRELACION".equals(l.getEstado()));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // HTTP 200 siempre + Reconciliación interna de eventos SIN_CORRELACION
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("HTTP y Reconciliación interna de eventos SIN_CORRELACION")
    class Reconciliacion {

        private static final String CHARGE_CREATION  = "charge.creation.succeeded";
        private static final String CUSTOMER_ID_REC  = "cus_test_rec_001";
        private static final String CHARGE_ID_REC    = "chr_test_rec_001";

        // ── Confirmación del contrato HTTP ────────────────────────────────────

        @Test
        @DisplayName("recibirWebhook devuelve siempre HTTP 200, incluso cuando guarda SIN_CORRELACION")
        void recibirWebhook_siempreDevuelve200_incluyendoSinCorrelacion() {
            when(suscripcionRepository.findByPreapprovalId(any())).thenReturn(Optional.empty());
            when(suscripcionRepository.findAllByCulqiCustomerIdOrderByIdDesc(any())).thenReturn(List.of());

            CulqiWebhookDTO payload = new CulqiWebhookDTO();
            payload.setId("evt_http200_test");
            payload.setType(CHARGE_CREATION);
            payload.setData("{\"id\":\"chr_sin_match\"}");

            var response = controller.recibirWebhook(payload);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
        }

        // ── Reconciliación exitosa ────────────────────────────────────────────

        @Test
        @DisplayName("reconciliarEventosPendientes — subscription_id obtenido de Culqi → PROCESADO")
        void reconciliacion_subIdEnCulqi_procesaCorrectamente() {
            // Simula evento que llegó sin subscription_id pero Culqi lo tiene en el cargo
            WebhookLog logEntry = WebhookLog.builder()
                    .id(100L).webhookId("evt_rec_001").tipo(CHARGE_CREATION)
                    .estado("SIN_CORRELACION").fechaProcesamiento(LocalDateTime.now())
                    .intentos(0).detalles("{\"id\":\"" + CHARGE_ID_REC + "\"}").build();

            when(webhookLogRepository.findSinCorrelacionPendientes(anyInt(), any()))
                    .thenReturn(List.of(logEntry));

            Map<String, Object> cargoConSubId = Map.of(
                    "id", CHARGE_ID_REC, "subscription_id", PREAPPROVAL_ID);
            when(culqiService.obtenerCargo(CHARGE_ID_REC)).thenReturn(cargoConSubId);
            when(suscripcionRepository.findByPreapprovalId(PREAPPROVAL_ID))
                    .thenReturn(Optional.of(susBotica));

            controller.reconciliarEventosPendientes();

            // Suscripción actualizada
            ArgumentCaptor<Suscripcion> sCaptor = ArgumentCaptor.forClass(Suscripcion.class);
            verify(suscripcionRepository).save(sCaptor.capture());
            assertThat(sCaptor.getValue().getEstado()).isEqualTo("ACTIVA");

            // WebhookLog marcado PROCESADO
            ArgumentCaptor<WebhookLog> lCaptor = ArgumentCaptor.forClass(WebhookLog.class);
            verify(webhookLogRepository, atLeastOnce()).save(lCaptor.capture());
            assertThat(lCaptor.getAllValues()).anyMatch(l -> "PROCESADO".equals(l.getEstado()));

            // Contador incrementado
            assertThat(logEntry.getIntentos()).isEqualTo(1);
        }

        // ── Reconciliación aún ambigua (no agota reintentos) ─────────────────

        @Test
        @DisplayName("reconciliarEventosPendientes — evento sigue ambiguo → queda SIN_CORRELACION (reintento pendiente)")
        void reconciliacion_sigueAmbigua_quedaSinCorrelacion() {
            WebhookLog logEntry = WebhookLog.builder()
                    .id(101L).webhookId("evt_rec_002").tipo(CHARGE_CREATION)
                    .estado("SIN_CORRELACION").fechaProcesamiento(LocalDateTime.now())
                    .intentos(1).detalles("{\"id\":\"" + CHARGE_ID_REC + "\"}").build();

            when(webhookLogRepository.findSinCorrelacionPendientes(anyInt(), any()))
                    .thenReturn(List.of(logEntry));
            when(culqiService.obtenerCargo(CHARGE_ID_REC)).thenReturn(null);
            when(suscripcionRepository.findByPreapprovalId(any())).thenReturn(Optional.empty());
            when(suscripcionRepository.findAllByCulqiCustomerIdOrderByIdDesc(any())).thenReturn(List.of());

            controller.reconciliarEventosPendientes();

            // Suscripción intacta
            verify(suscripcionRepository, never()).save(any(Suscripcion.class));
            // Estado sigue siendo SIN_CORRELACION (intentos=2 < MAX=3)
            assertThat(logEntry.getIntentos()).isEqualTo(2);
            assertThat(logEntry.getEstado()).isEqualTo("SIN_CORRELACION");
        }

        // ── Agota reintentos → PENDIENTE_REVISION ────────────────────────────

        @Test
        @DisplayName("reconciliarEventosPendientes — agota MAX_INTENTOS → PENDIENTE_REVISION")
        void reconciliacion_agotaIntentos_marcaPendienteRevision() {
            // intentos=2: tras incrementar llega a 3 = MAX → PENDIENTE_REVISION
            WebhookLog logEntry = WebhookLog.builder()
                    .id(102L).webhookId("evt_rec_003").tipo(CHARGE_CREATION)
                    .estado("SIN_CORRELACION").fechaProcesamiento(LocalDateTime.now())
                    .intentos(2).detalles("{\"id\":\"" + CHARGE_ID_REC + "\"}").build();

            when(webhookLogRepository.findSinCorrelacionPendientes(anyInt(), any()))
                    .thenReturn(List.of(logEntry));
            when(culqiService.obtenerCargo(CHARGE_ID_REC)).thenReturn(null);
            when(suscripcionRepository.findByPreapprovalId(any())).thenReturn(Optional.empty());
            when(suscripcionRepository.findAllByCulqiCustomerIdOrderByIdDesc(any())).thenReturn(List.of());

            controller.reconciliarEventosPendientes();

            assertThat(logEntry.getIntentos()).isEqualTo(3);
            assertThat(logEntry.getEstado()).isEqualTo("PENDIENTE_REVISION");
            verify(webhookLogRepository).save(logEntry);
        }

        // ── Eventos PROCESADO no re-encolados ────────────────────────────────

        @Test
        @DisplayName("reconciliarEventosPendientes — no hay eventos SIN_CORRELACION → no procesa nada")
        void reconciliacion_sinEventosPendientes_noProcesaNada() {
            when(webhookLogRepository.findSinCorrelacionPendientes(anyInt(), any()))
                    .thenReturn(List.of());

            controller.reconciliarEventosPendientes();

            verify(culqiService, never()).obtenerCargo(any());
            verify(suscripcionRepository, never()).save(any(Suscripcion.class));
        }

        // ── Idempotencia durante reintento de Culqi ───────────────────────────

        @Test
        @DisplayName("evento duplicado durante reintento de Culqi → idempotente, no reprocesa")
        void reconciliacion_eventoDuplicadoDuranteReintento_idempotente() {
            // Simula que el mismo eventId ya fue procesado en el scheduler anterior
            String eventId = "evt_dup_rec_001";
            when(webhookLogRepository.existsByWebhookIdAndEstado(eventId, "PROCESADO")).thenReturn(true);

            CulqiWebhookDTO payload = new CulqiWebhookDTO();
            payload.setId(eventId);
            payload.setType(CHARGE_CREATION);
            payload.setData("{\"id\":\"chr_test_dup\",\"subscription_id\":\"" + PREAPPROVAL_ID + "\"}");

            controller.recibirWebhook(payload);

            // No busca suscripción — idempotencia por PROCESADO en WebhookLog
            verify(suscripcionRepository, never()).findByPreapprovalId(any());
            verify(suscripcionRepository, never()).save(any(Suscripcion.class));
        }

        // ── Correlación tras upgrade/downgrade ───────────────────────────────

        @Test
        @DisplayName("subscription.charge.succeeded (recurrente) incluye subscription_id → correlación directa, no usa customer_id")
        void chargeRecurrente_trasUpgrade_usaSubscriptionId_directamente() {
            // Después de un upgrade, el cobro recurrente de la nueva suscripción siempre
            // incluye subscription_id en el payload → estrategia 1, sin ambigüedad.
            String nuevoSubId = "sxn_test_NUEVO_POST_UPGRADE";
            Suscripcion susNueva = buildSuscripcion(60L, "botica-abc", nuevoSubId, "ACTIVA");
            when(suscripcionRepository.findByPreapprovalId(nuevoSubId)).thenReturn(Optional.of(susNueva));

            enviarWebhook("subscription.charge.succeeded",
                    "{\"id\":\"chr_rec_900\",\"subscription_id\":\"" + nuevoSubId + "\"}");

            ArgumentCaptor<Suscripcion> captor = ArgumentCaptor.forClass(Suscripcion.class);
            verify(suscripcionRepository).save(captor.capture());
            assertThat(captor.getValue().getId()).isEqualTo(60L);
            assertThat(captor.getValue().getEstado()).isEqualTo("ACTIVA");
            // La estrategia 1 resuelve sin necesitar customer_id
            verify(suscripcionRepository, never()).findAllByCulqiCustomerIdOrderByIdDesc(any());
        }

        @Test
        @DisplayName("charge.creation.succeeded (primer cobro) tras upgrade: dos suscripciones con mismo customer_id → SIN_CORRELACION → reconciliación resuelve por subscription_id")
        void chargeCreacion_trasPrimerCobroUpgrade_dosSuscripciones_reconciliacionResuelvePorSubId() {
            // Primer cobro de nueva suscripción tras upgrade llega sin subscription_id.
            // customer_id ambiguo (vieja CANCELADA + nueva ACTIVA) → SIN_CORRELACION.
            // Reconciliación llama a Culqi y obtiene subscription_id → PROCESADO.
            String customerIdUpgrade = "cus_upgrade_001";
            String nuevoSubId        = "sxn_test_NUEVO_UPGRADE";

            Suscripcion susAntigua = buildSuscripcionConCustomerId(70L, "botica-abc",
                    "sxn_test_ANTIGUO_UPGRADE", "CANCELADA", customerIdUpgrade);
            Suscripcion susNueva   = buildSuscripcionConCustomerId(71L, "botica-abc",
                    nuevoSubId, "ACTIVA", customerIdUpgrade);

            // 1ª fase: webhook llega — ambigüedad → SIN_CORRELACION
            when(suscripcionRepository.findByPreapprovalId(any())).thenReturn(Optional.empty());
            when(suscripcionRepository.findAllByCulqiCustomerIdOrderByIdDesc(customerIdUpgrade))
                    .thenReturn(List.of(susNueva, susAntigua));  // 2 suscripciones → ambiguo

            enviarWebhook(CHARGE_CREATION,
                    "{\"id\":\"chr_upgrade_001\",\"customer_id\":\"" + customerIdUpgrade + "\"}");

            // Suscripción intacta en la primera pasada
            verify(suscripcionRepository, never()).save(any(Suscripcion.class));

            // 2ª fase: reconciliación obtiene subscription_id desde Culqi → resuelve
            WebhookLog logEntry = WebhookLog.builder()
                    .id(103L).webhookId("evt_upgrade_rec").tipo(CHARGE_CREATION)
                    .estado("SIN_CORRELACION").fechaProcesamiento(LocalDateTime.now())
                    .intentos(0)
                    .detalles("{\"id\":\"chr_upgrade_001\",\"customer_id\":\"" + customerIdUpgrade + "\"}")
                    .build();

            when(webhookLogRepository.findSinCorrelacionPendientes(anyInt(), any()))
                    .thenReturn(List.of(logEntry));
            // Culqi devuelve el subscription_id en el cargo completo
            when(culqiService.obtenerCargo("chr_upgrade_001"))
                    .thenReturn(Map.of("id", "chr_upgrade_001", "subscription_id", nuevoSubId));
            when(suscripcionRepository.findByPreapprovalId(nuevoSubId))
                    .thenReturn(Optional.of(susNueva));

            controller.reconciliarEventosPendientes();

            ArgumentCaptor<Suscripcion> captor = ArgumentCaptor.forClass(Suscripcion.class);
            verify(suscripcionRepository).save(captor.capture());
            assertThat(captor.getValue().getId()).isEqualTo(71L);   // solo la nueva
            assertThat(captor.getValue().getEstado()).isEqualTo("ACTIVA");

            ArgumentCaptor<WebhookLog> lCaptor = ArgumentCaptor.forClass(WebhookLog.class);
            verify(webhookLogRepository, atLeastOnce()).save(lCaptor.capture());
            assertThat(lCaptor.getAllValues()).anyMatch(l -> "PROCESADO".equals(l.getEstado()));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Verificación vía API de Culqi — nuevos tests
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Verificación vía API de Culqi")
    class VerificacionAPI {

        private static final String CHARGE_OK     = "charge.creation.succeeded";
        private static final String CHARGE_FAILED = "subscription.charge.failed";
        private static final String CANCEL_OK     = "subscription.cancel.succeeded";
        private static final String CHR_ID        = "chr_test_ver_001";
        private static final String SUB_CANCEL    = "sxn_test_cancel_ver";

        private String payloadConChr(String chrId) {
            return "{\"id\":\"" + chrId + "\",\"subscription_id\":\"" + PREAPPROVAL_ID + "\"}";
        }

        private String payloadCancelReal(String subId) {
            return "{\"message\":{\"object\":{\"subsId\":\"" + subId + "\"}}}";
        }

        // ── charge.succeeded: cargo no existe en Culqi ────────────────────────

        @Test
        @DisplayName("charge.succeeded — cargo no existe en Culqi → PENDIENTE_REVISION, no activa")
        void chargeSucceeded_cargoNoExisteEnCulqi_marcaPendienteRevision() {
            when(suscripcionRepository.findByPreapprovalId(PREAPPROVAL_ID))
                    .thenReturn(Optional.of(susBotica));
            when(culqiService.obtenerCargo(CHR_ID)).thenReturn(null);

            enviarWebhook(CHARGE_OK, payloadConChr(CHR_ID));

            verify(suscripcionRepository, never()).save(any(Suscripcion.class));
            ArgumentCaptor<WebhookLog> logCaptor = ArgumentCaptor.forClass(WebhookLog.class);
            verify(webhookLogRepository, atLeastOnce()).save(logCaptor.capture());
            assertThat(logCaptor.getAllValues())
                    .anyMatch(l -> "PENDIENTE_REVISION".equals(l.getEstado()));
        }

        @Test
        @DisplayName("charge.succeeded — cargo existe pero subscription_id no coincide → PENDIENTE_REVISION")
        void chargeSucceeded_subIdMismatch_marcaPendienteRevision() {
            when(suscripcionRepository.findByPreapprovalId(PREAPPROVAL_ID))
                    .thenReturn(Optional.of(susBotica));
            Map<String, Object> cargoAjeno = new HashMap<>();
            cargoAjeno.put("subscription_id", "sxn_otro_tenant_999");
            when(culqiService.obtenerCargo(CHR_ID)).thenReturn(cargoAjeno);

            enviarWebhook(CHARGE_OK, payloadConChr(CHR_ID));

            verify(suscripcionRepository, never()).save(any(Suscripcion.class));
            ArgumentCaptor<WebhookLog> logCaptor = ArgumentCaptor.forClass(WebhookLog.class);
            verify(webhookLogRepository, atLeastOnce()).save(logCaptor.capture());
            assertThat(logCaptor.getAllValues())
                    .anyMatch(l -> "PENDIENTE_REVISION".equals(l.getEstado()));
        }

        @Test
        @DisplayName("charge.succeeded — cargo existe, subscription_id coincide → procesa normalmente")
        void chargeSucceeded_subIdCorrecto_procesaNormal() {
            when(suscripcionRepository.findByPreapprovalId(PREAPPROVAL_ID))
                    .thenReturn(Optional.of(susBotica));
            Map<String, Object> cargoOk = new HashMap<>();
            cargoOk.put("subscription_id", PREAPPROVAL_ID);
            when(culqiService.obtenerCargo(CHR_ID)).thenReturn(cargoOk);

            enviarWebhook(CHARGE_OK, payloadConChr(CHR_ID));

            ArgumentCaptor<Suscripcion> captor = ArgumentCaptor.forClass(Suscripcion.class);
            verify(suscripcionRepository).save(captor.capture());
            assertThat(captor.getValue().getEstado()).isEqualTo("ACTIVA");
        }

        // ── charge.failed: cargo no existe en Culqi ───────────────────────────

        @Test
        @DisplayName("charge.failed — cargo no existe en Culqi → PENDIENTE_REVISION, no suspende")
        void chargeFailed_cargoNoExisteEnCulqi_marcaPendienteRevision() {
            when(suscripcionRepository.findByPreapprovalId(PREAPPROVAL_ID))
                    .thenReturn(Optional.of(susBotica));
            when(culqiService.obtenerCargo(CHR_ID)).thenReturn(null);

            enviarWebhook(CHARGE_FAILED,
                    "{\"id\":\"" + CHR_ID + "\",\"subscription_id\":\"" + PREAPPROVAL_ID + "\"}");

            verify(suscripcionRepository, never()).save(any(Suscripcion.class));
            ArgumentCaptor<WebhookLog> logCaptor = ArgumentCaptor.forClass(WebhookLog.class);
            verify(webhookLogRepository, atLeastOnce()).save(logCaptor.capture());
            assertThat(logCaptor.getAllValues())
                    .anyMatch(l -> "PENDIENTE_REVISION".equals(l.getEstado()));
        }

        // ── cancel.succeeded: Culqi reporta suscripción activa ────────────────

        @Test
        @DisplayName("cancel.succeeded — Culqi reporta suscripción como 'active' → PENDIENTE_REVISION")
        void cancelSucceeded_susActivaEnCulqi_marcaPendienteRevision() {
            Suscripcion sus = buildSuscripcion(80L, "tenant-v", SUB_CANCEL, "ACTIVA");
            when(suscripcionRepository.findByPreapprovalId(SUB_CANCEL)).thenReturn(Optional.of(sus));
            Map<String, Object> susActivaEnCulqi = new HashMap<>();
            susActivaEnCulqi.put("status", "active");
            when(culqiService.obtenerSuscripcion(SUB_CANCEL)).thenReturn(susActivaEnCulqi);

            enviarWebhook(CANCEL_OK, payloadCancelReal(SUB_CANCEL));

            verify(suscripcionRepository, never()).save(any(Suscripcion.class));
            ArgumentCaptor<WebhookLog> logCaptor = ArgumentCaptor.forClass(WebhookLog.class);
            verify(webhookLogRepository, atLeastOnce()).save(logCaptor.capture());
            assertThat(logCaptor.getAllValues())
                    .anyMatch(l -> "PENDIENTE_REVISION".equals(l.getEstado()));
        }

        @Test
        @DisplayName("cancel.succeeded — Culqi devuelve 404 (suscripción ya eliminada) → procede con cancelación local")
        void cancelSucceeded_sus404EnCulqi_procedeCancelacion() {
            // null = 404 desde CulqiServiceImpl: suscripción confirmada como eliminada en Culqi
            Suscripcion sus = buildSuscripcion(81L, "tenant-v", SUB_CANCEL, "ACTIVA");
            when(suscripcionRepository.findByPreapprovalId(SUB_CANCEL)).thenReturn(Optional.of(sus));
            when(culqiService.obtenerSuscripcion(SUB_CANCEL)).thenReturn(null);

            enviarWebhook(CANCEL_OK, payloadCancelReal(SUB_CANCEL));

            ArgumentCaptor<Suscripcion> captor = ArgumentCaptor.forClass(Suscripcion.class);
            verify(suscripcionRepository).save(captor.capture());
            assertThat(captor.getValue().getEstado())
                    .isIn("CANCELACION_PENDIENTE", "CANCELADA");
        }

        @Test
        @DisplayName("cancel.succeeded — API de Culqi no disponible (excepción) → PENDIENTE_REVISION, no cancela")
        void cancelSucceeded_culqiApiNoDisponible_marcaPendienteRevision() {
            Suscripcion sus = buildSuscripcion(83L, "tenant-v", SUB_CANCEL, "ACTIVA");
            when(suscripcionRepository.findByPreapprovalId(SUB_CANCEL)).thenReturn(Optional.of(sus));
            when(culqiService.obtenerSuscripcion(SUB_CANCEL))
                    .thenThrow(new RuntimeException("Culqi API no disponible al verificar suscripción " + SUB_CANCEL));

            enviarWebhook(CANCEL_OK, payloadCancelReal(SUB_CANCEL));

            // No debe cancelar la suscripción local sin verificación
            verify(suscripcionRepository, never()).save(any(Suscripcion.class));
            ArgumentCaptor<WebhookLog> logCaptor = ArgumentCaptor.forClass(WebhookLog.class);
            verify(webhookLogRepository, atLeastOnce()).save(logCaptor.capture());
            assertThat(logCaptor.getAllValues())
                    .anyMatch(l -> "PENDIENTE_REVISION".equals(l.getEstado()));
        }

        @Test
        @DisplayName("cancel.succeeded — API de Culqi retorna HTTP 500 → PENDIENTE_REVISION, no cancela")
        void cancelSucceeded_culqiApiHttp500_marcaPendienteRevision() {
            Suscripcion sus = buildSuscripcion(84L, "tenant-v", SUB_CANCEL, "ACTIVA");
            when(suscripcionRepository.findByPreapprovalId(SUB_CANCEL)).thenReturn(Optional.of(sus));
            when(culqiService.obtenerSuscripcion(SUB_CANCEL))
                    .thenThrow(new RuntimeException("Culqi API: HTTP 500 al obtener suscripción " + SUB_CANCEL));

            enviarWebhook(CANCEL_OK, payloadCancelReal(SUB_CANCEL));

            verify(suscripcionRepository, never()).save(any(Suscripcion.class));
            ArgumentCaptor<WebhookLog> logCaptor = ArgumentCaptor.forClass(WebhookLog.class);
            verify(webhookLogRepository, atLeastOnce()).save(logCaptor.capture());
            assertThat(logCaptor.getAllValues())
                    .anyMatch(l -> "PENDIENTE_REVISION".equals(l.getEstado()));
        }

        @Test
        @DisplayName("cancel.succeeded — Culqi reporta 'canceled' → procede con cancelación local")
        void cancelSucceeded_susCanceladaEnCulqi_procedeCancelacion() {
            Suscripcion sus = buildSuscripcion(82L, "tenant-v", SUB_CANCEL, "ACTIVA");
            when(suscripcionRepository.findByPreapprovalId(SUB_CANCEL)).thenReturn(Optional.of(sus));
            Map<String, Object> susCanceladaEnCulqi = new HashMap<>();
            susCanceladaEnCulqi.put("status", "canceled");
            when(culqiService.obtenerSuscripcion(SUB_CANCEL)).thenReturn(susCanceladaEnCulqi);

            enviarWebhook(CANCEL_OK, payloadCancelReal(SUB_CANCEL));

            ArgumentCaptor<Suscripcion> captor = ArgumentCaptor.forClass(Suscripcion.class);
            verify(suscripcionRepository).save(captor.capture());
            assertThat(captor.getValue().getEstado())
                    .isIn("CANCELACION_PENDIENTE", "CANCELADA");
        }

        // ── Idempotencia concurrente ───────────────────────────────────────────

        @Test
        @DisplayName("dos requests concurrentes con mismo eventId → DataIntegrityViolationException → 200, no procesa")
        void eventoConcurrente_dataIntegrityException_devuelve200SinProcesar() {
            String eventId = "evt_concurrent_sec_001";
            when(webhookLogRepository.existsByWebhookIdAndEstado(eventId, "PROCESADO")).thenReturn(false);
            when(webhookLogRepository.save(any())).thenThrow(
                    new DataIntegrityViolationException("duplicate key value violates unique constraint"));

            CulqiWebhookDTO payload = new CulqiWebhookDTO();
            payload.setId(eventId);
            payload.setType(CHARGE_OK);
            payload.setData("{\"id\":\"chr_con_001\",\"subscription_id\":\"" + PREAPPROVAL_ID + "\"}");

            var response = controller.recibirWebhook(payload);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            verify(suscripcionRepository, never()).save(any(Suscripcion.class));
            verify(emailService, never()).enviarEmailSuscripcion(any(), any(), any(), any());
        }

        // ── Reconciliación de cancel.succeeded ────────────────────────────────

        @Test
        @DisplayName("reconciliarEventosPendientes — cancel.succeeded SIN_CORRELACION → cuando se encuentra la suscripción → PROCESADO")
        void reconciliacion_cancelSucceeded_sinCorrelacion_procesaCorrectamente() {
            String subIdReconciliar = "sxn_test_rec_cancel_001";
            String payloadCancel = "{\"message\":{\"object\":{\"subsId\":\"" + subIdReconciliar + "\"}}}";

            WebhookLog logEntry = WebhookLog.builder()
                    .id(200L).webhookId("evt_cancel_rec_001").tipo(CANCEL_OK)
                    .estado("SIN_CORRELACION").fechaProcesamiento(LocalDateTime.now())
                    .intentos(0).detalles(payloadCancel).build();

            Suscripcion sus = buildSuscripcion(90L, "tenant-cancel-rec", subIdReconciliar, "ACTIVA");

            when(webhookLogRepository.findSinCorrelacionPendientes(anyInt(), any()))
                    .thenReturn(List.of(logEntry));
            when(suscripcionRepository.findByPreapprovalId(subIdReconciliar))
                    .thenReturn(Optional.of(sus));
            when(culqiService.obtenerSuscripcion(subIdReconciliar)).thenReturn(null);

            controller.reconciliarEventosPendientes();

            ArgumentCaptor<Suscripcion> captor = ArgumentCaptor.forClass(Suscripcion.class);
            verify(suscripcionRepository).save(captor.capture());
            assertThat(captor.getValue().getEstado())
                    .isIn("CANCELACION_PENDIENTE", "CANCELADA");

            ArgumentCaptor<WebhookLog> lCaptor = ArgumentCaptor.forClass(WebhookLog.class);
            verify(webhookLogRepository, atLeastOnce()).save(lCaptor.capture());
            assertThat(lCaptor.getAllValues()).anyMatch(l -> "PROCESADO".equals(l.getEstado()));
            assertThat(logEntry.getIntentos()).isEqualTo(1);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void enviarWebhook(String type, String dataJson) {
        CulqiWebhookDTO payload = new CulqiWebhookDTO();
        payload.setId("evt_test_" + System.nanoTime());
        payload.setType(type);
        payload.setData(dataJson);
        controller.recibirWebhook(payload);
    }

    private Suscripcion buildSuscripcion(Long id, String tenantId, String preapprovalId, String estado) {
        return Suscripcion.builder()
                .id(id)
                .tenantId(tenantId)
                .preapprovalId(preapprovalId)
                .planId("BASICO")
                .precioMensual(BigDecimal.valueOf(89))
                .estado(estado)
                .usuarioPrincipal(usuario)
                .fechaProximoCobro(LocalDateTime.now().plusDays(30))
                .currentPeriodStart(LocalDateTime.now())
                .currentPeriodEnd(LocalDateTime.now().plusMonths(1))
                .build();
    }

    private Suscripcion buildSuscripcionConCustomerId(Long id, String tenantId, String preapprovalId,
                                                      String estado, String culqiCustomerId) {
        return Suscripcion.builder()
                .id(id)
                .tenantId(tenantId)
                .preapprovalId(preapprovalId)
                .planId("BASICO")
                .precioMensual(BigDecimal.valueOf(89))
                .estado(estado)
                .usuarioPrincipal(usuario)
                .culqiCustomerId(culqiCustomerId)
                .fechaProximoCobro(LocalDateTime.now().plusDays(30))
                .currentPeriodStart(LocalDateTime.now())
                .currentPeriodEnd(LocalDateTime.now().plusMonths(1))
                .build();
    }
}
