package com.stockflow.controller;

import com.stockflow.config.properties.CulqiProperties;
import com.stockflow.dto.CulqiSuscribirRequestDTO;
import com.stockflow.dto.CulqiSuscribirResponseDTO;
import com.stockflow.entity.Suscripcion;
import com.stockflow.entity.Usuario;
import com.stockflow.exception.BadRequestException;
import com.stockflow.repository.SuscripcionRepository;
import com.stockflow.service.CulqiService;
import com.stockflow.service.EmailService;
import com.stockflow.service.SucursalService;
import com.stockflow.service.SuscripcionService;
import com.stockflow.service.UsuarioService;
import com.stockflow.util.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Verifica el orden seguro de upgradePro():
 *   Fase 1 — resolver customer + registrar tarjeta (ANTES de cancelar Básico)
 *   Fase 2 — cancelar Básico
 *   Fase 3 — crear PRO
 *   Fase 4 — persistir localmente + enviar email
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CulqiController.upgradePro() — orden seguro y resiliencia")
class CulqiUpgradeProSafeOrderTest {

    private static final String TENANT_ID       = "vet-59ba";
    private static final Long   USUARIO_ID      = 5L;
    private static final String EMAIL_REAL      = "joaquincastillotello2001@gmail.com";
    private static final String EMAIL_SCOPED    = "joaquincastillotello2001+vet-59ba@gmail.com";
    private static final String CUSTOMER_ID     = "cus_test_HczFohTtZcIM75HT";
    private static final String CARD_ID         = "crd_test_abc123";
    private static final String SUB_BASICO_ID   = "sxn_test_9igO0LAPNLLdz9hX";
    private static final String SUB_PRO_ID      = "sxn_test_pro_xyz";
    private static final String PLAN_PRO_ID     = "pln_test_pro_001";
    private static final String PLAN_BASICO_ID  = "pln_test_bas_001";

    @Mock private CulqiService         culqiService;
    @Mock private CulqiProperties      culqiProperties;
    @Mock private SuscripcionService   suscripcionService;
    @Mock private SuscripcionRepository suscripcionRepository;
    @Mock private UsuarioService       usuarioService;
    @Mock private SucursalService      sucursalService;
    @Mock private EmailService         emailService;

    @InjectMocks private CulqiController controller;

    private Usuario  usuario;
    private Suscripcion suscripcionBasico;
    private CulqiSuscribirRequestDTO request;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(TENANT_ID);
        TenantContext.setCurrentUserId(USUARIO_ID);

        usuario = new Usuario();
        usuario.setId(USUARIO_ID);
        usuario.setEmail(EMAIL_REAL);
        usuario.setNombre("Joaquín");
        usuario.setApellido("Castillo");

        suscripcionBasico = Suscripcion.builder()
                .id(10L)
                .tenantId(TENANT_ID)
                .planId("BASICO")
                .estado("ACTIVA")
                .preapprovalId(SUB_BASICO_ID)
                .usuarioPrincipal(usuario)
                .build();

        request = new CulqiSuscribirRequestDTO();
        request.setTokenId("tok_test_nueva_tarjeta");
        request.setPlanId("PRO");

        // Defaults comunes
        when(culqiProperties.getPlanIdPro()).thenReturn(PLAN_PRO_ID);
        when(culqiProperties.getPlanIdBasico()).thenReturn(PLAN_BASICO_ID);
        when(culqiProperties.getPrecioPro()).thenReturn(new BigDecimal("129.00"));
        when(culqiProperties.getPrecioBasico()).thenReturn(new BigDecimal("89.00"));
        when(usuarioService.obtenerUsuarioPorId(USUARIO_ID)).thenReturn(Optional.of(usuario));
        when(suscripcionRepository.findFirstByTenantIdOrderByIdDesc(TENANT_ID))
                .thenReturn(Optional.of(suscripcionBasico));
        when(culqiService.crearCliente(eq(EMAIL_SCOPED), any(), any(), any()))
                .thenReturn(CUSTOMER_ID);
        when(culqiService.crearTarjeta(eq(CUSTOMER_ID), anyString()))
                .thenReturn(Map.of("id", CARD_ID,
                        "source", Map.of("last_four", "9999",
                                "iin", Map.of("card_brand", "MASTERCARD"))));
        when(culqiService.crearSuscripcion(eq(CARD_ID), eq(PLAN_PRO_ID), any()))
                .thenReturn(SUB_PRO_ID);
        when(suscripcionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sucursalService.inicializarPrincipal(any())).thenReturn(null);
        when(sucursalService.desbloquearSucursalesAdicionales(any())).thenReturn(0);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── Flujo exitoso ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Upgrade PRO exitoso")
    class UpgradeExitoso {

        @Test
        @DisplayName("retorna suscripción ACTIVA con planId=PRO")
        void retornaProActiva() {
            ResponseEntity<CulqiSuscribirResponseDTO> resp = controller.upgradePro(request);

            assertThat(resp.getStatusCode().value()).isEqualTo(200);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().getPlanId()).isEqualTo("PRO");
            assertThat(resp.getBody().getEstado()).isEqualTo("ACTIVA");
            assertThat(resp.getBody().getCulqiSubscriptionId()).isEqualTo(SUB_PRO_ID);
        }

        @Test
        @DisplayName("reutiliza customer existente cus_test_HczFohTtZcIM75HT sin crear duplicado")
        void reutilizaCustomerExistente() {
            controller.upgradePro(request);

            // crearCliente debe llamarse con el email scoped (la búsqueda interna reutiliza el existente)
            verify(culqiService).crearCliente(eq(EMAIL_SCOPED), any(), any(), any());
        }

        @Test
        @DisplayName("el tenant activo sigue siendo vet-59ba")
        void tenantSigueActivo() {
            controller.upgradePro(request);
            assertThat(TenantContext.getCurrentTenant()).isEqualTo(TENANT_ID);
        }

        @Test
        @DisplayName("email de confirmación se envía al email REAL, no al tenant-scoped")
        void emailSeEnviaAlRealEmail() {
            controller.upgradePro(request);

            verify(emailService).enviarConfirmacionSuscripcionCulqi(
                    eq(EMAIL_REAL),
                    any(), any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("email NO se envía al email tenant-scoped")
        void emailNuncaVaAlEmailScopado() {
            controller.upgradePro(request);

            verify(emailService, never()).enviarConfirmacionSuscripcionCulqi(
                    eq(EMAIL_SCOPED),
                    any(), any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("email se envía DESPUÉS de inicializarPrincipal (no antes)")
        void emailOcurreDespuesDeInicializarPrincipal() {
            InOrder inOrder = inOrder(sucursalService, emailService);

            controller.upgradePro(request);

            inOrder.verify(sucursalService).inicializarPrincipal(TENANT_ID);
            inOrder.verify(emailService).enviarConfirmacionSuscripcionCulqi(
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        }
    }

    // ── Orden de operaciones ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Orden seguro: customer+tarjeta ANTES de cancelar Básico")
    class OrdenOperaciones {

        @Test
        @DisplayName("customer y tarjeta se resuelven ANTES de cancelar Básico")
        void customerYTarjetaAntesDeCancel() {
            InOrder inOrder = inOrder(culqiService);

            controller.upgradePro(request);

            inOrder.verify(culqiService).crearCliente(any(), any(), any(), any());
            inOrder.verify(culqiService).crearTarjeta(any(), any());
            inOrder.verify(culqiService).cancelarSuscripcion(SUB_BASICO_ID);
            inOrder.verify(culqiService).crearSuscripcion(any(), any(), any());
        }

        @Test
        @DisplayName("si falla crearCliente, NO cancela la suscripción Básica")
        void failCrearCliente_noCancelaBasico() {
            when(culqiService.crearCliente(any(), any(), any(), any()))
                    .thenThrow(new BadRequestException("Error resolving customer"));

            assertThatThrownBy(() -> controller.upgradePro(request))
                    .isInstanceOf(BadRequestException.class);

            verify(culqiService, never()).cancelarSuscripcion(any());
        }

        @Test
        @DisplayName("si falla crearTarjeta, NO cancela la suscripción Básica")
        void failCrearTarjeta_noCancelaBasico() {
            when(culqiService.crearTarjeta(any(), any()))
                    .thenThrow(new BadRequestException("Token inválido"));

            assertThatThrownBy(() -> controller.upgradePro(request))
                    .isInstanceOf(BadRequestException.class);

            verify(culqiService, never()).cancelarSuscripcion(any());
        }
    }

    // ── Fallo después de cancelar Básico ─────────────────────────────────────

    @Nested
    @DisplayName("Recuperación tras fallo de PRO")
    class RecuperacionTrasFalloPro {

        @Test
        @DisplayName("si crearSuscripcion PRO falla, lanza BadRequestException con mensaje claro")
        void failCrearPro_lanzaConMensaje() {
            when(culqiService.crearSuscripcion(eq(CARD_ID), eq(PLAN_PRO_ID), any()))
                    .thenThrow(new BadRequestException("Plan PRO error"));

            // Recovery BASICO succeeds
            when(culqiService.crearSuscripcion(eq(CARD_ID), eq(PLAN_BASICO_ID), any()))
                    .thenReturn("sxn_test_recovery_bas");

            assertThatThrownBy(() -> controller.upgradePro(request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("No se pudo activar el plan Pro");
        }

        @Test
        @DisplayName("si crearSuscripcion PRO falla, intenta recuperar con plan Básico")
        void failCrearPro_intentaRecuperarBasico() {
            when(culqiService.crearSuscripcion(eq(CARD_ID), eq(PLAN_PRO_ID), any()))
                    .thenThrow(new BadRequestException("Plan PRO error"));
            when(culqiService.crearSuscripcion(eq(CARD_ID), eq(PLAN_BASICO_ID), any()))
                    .thenReturn("sxn_test_recovery_bas");

            try {
                controller.upgradePro(request);
            } catch (BadRequestException ignored) {}

            // Verifica que intentó crear un Básico de recuperación
            verify(culqiService).crearSuscripcion(eq(CARD_ID), eq(PLAN_BASICO_ID), any());
        }

        @Test
        @DisplayName("si PRO falla, NO envía email de upgrade exitoso")
        void failCrearPro_noEnviaEmail() {
            when(culqiService.crearSuscripcion(eq(CARD_ID), eq(PLAN_PRO_ID), any()))
                    .thenThrow(new BadRequestException("Plan PRO error"));
            when(culqiService.crearSuscripcion(eq(CARD_ID), eq(PLAN_BASICO_ID), any()))
                    .thenReturn("sxn_test_recovery_bas");

            try {
                controller.upgradePro(request);
            } catch (BadRequestException ignored) {}

            verify(emailService, never()).enviarConfirmacionSuscripcionCulqi(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("si inicializarPrincipal falla, NO envía email de confirmación")
        void failInicializarPrincipal_noEnviaEmail() {
            when(sucursalService.inicializarPrincipal(TENANT_ID))
                    .thenThrow(new RuntimeException("bloqueada_por_plan null"));

            try {
                controller.upgradePro(request);
            } catch (RuntimeException ignored) {}

            verify(emailService, never()).enviarConfirmacionSuscripcionCulqi(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        }
    }
}
