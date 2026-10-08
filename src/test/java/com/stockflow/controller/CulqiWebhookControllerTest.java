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
}
