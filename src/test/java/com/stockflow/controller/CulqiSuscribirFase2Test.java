package com.stockflow.controller;

import com.stockflow.config.properties.CulqiProperties;
import com.stockflow.dto.CulqiSuscribirRequestDTO;
import com.stockflow.entity.PlanCatalogo;
import com.stockflow.entity.Suscripcion;
import com.stockflow.entity.Usuario;
import com.stockflow.exception.BadRequestException;
import com.stockflow.repository.PlanCatalogoRepository;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios para Fase 2 — dual routing y protección TRIAL.
 * Cubre: POST /suscribir (TRIAL guard, catálogo, legado) y POST /upgrade-pro + /downgrade-basico (catálogo bloqueado).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Culqi Fase 2 — Dual routing y protección TRIAL")
class CulqiSuscribirFase2Test {

    @Mock private CulqiService           culqiService;
    @Mock private CulqiProperties        culqiProperties;
    @Mock private PlanCatalogoRepository planCatalogoRepository;
    @Mock private SuscripcionRepository  suscripcionRepository;
    @Mock private SuscripcionService     suscripcionService;
    @Mock private UsuarioService         usuarioService;
    @Mock private SucursalService        sucursalService;
    @Mock private EmailService           emailService;

    @InjectMocks
    private CulqiController controller;

    private MockedStatic<TenantContext> tenantContextMock;

    private static final String TENANT_ID  = "tenant-vet-001";
    private static final long   USUARIO_ID = 42L;

    @BeforeEach
    void setUp() {
        tenantContextMock = mockStatic(TenantContext.class);
        tenantContextMock.when(TenantContext::getCurrentTenant).thenReturn(TENANT_ID);
        tenantContextMock.when(TenantContext::getCurrentUserId).thenReturn(USUARIO_ID);

        // Usuario base
        Usuario usuario = new Usuario();
        usuario.setId(USUARIO_ID);
        usuario.setEmail("vet@example.com");
        usuario.setNombre("Dr. Vet");
        when(usuarioService.obtenerUsuarioPorId(USUARIO_ID)).thenReturn(Optional.of(usuario));

        // Culqi defaults
        when(culqiProperties.getPrecioBasico()).thenReturn(new BigDecimal("89.00"));
        when(culqiProperties.getPrecioPro()).thenReturn(new BigDecimal("169.00"));
        when(culqiProperties.getPlanIdBasico()).thenReturn("plan_legacy_basico");
        when(culqiProperties.getPlanIdPro()).thenReturn("plan_legacy_pro");
        when(culqiService.crearCliente(anyString(), any(), any(), any())).thenReturn("cus_test");
        when(culqiService.crearTarjeta(anyString(), anyString())).thenReturn(Map.of("id", "crd_test"));
        when(culqiService.crearSuscripcion(anyString(), anyString(), any())).thenReturn("sub_test_001");
    }

    @AfterEach
    void tearDown() {
        tenantContextMock.close();
    }

    // ── Suscripción voluntaria durante TRIAL ────────────────────────────────

    @Nested
    @DisplayName("Contratación voluntaria durante TRIAL")
    class SuscripcionVoluntariaTrial {

        @Test
        @DisplayName("Estado TRIAL + catálogo → suscripción completada, usa plan del catálogo")
        void trialConCatalogo_suscribeYUsaPlanCatalogo() {
            Suscripcion trial = suscripcionEnEstado("TRIAL", "VETERINARIA_BASICO");
            when(suscripcionRepository.findFirstByTenantIdOrderByIdDesc(TENANT_ID))
                    .thenReturn(Optional.of(trial));
            when(suscripcionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PlanCatalogo vetBasico = planCatalogo("VETERINARIA_BASICO", new BigDecimal("109.00"), "plan_culqi_109");
            when(planCatalogoRepository.findByCodigoAndActivoTrue("VETERINARIA_BASICO"))
                    .thenReturn(Optional.of(vetBasico));

            var resp = controller.suscribir(request("BASICO", "tok_trial_vet"));

            // El flujo de Culqi debe completarse normalmente
            verify(culqiService).crearSuscripcion(eq("crd_test"), eq("plan_culqi_109"), any());
            assertThat(resp.getBody().getEstado()).isEqualTo("ACTIVA");
            assertThat(resp.getBody().getPrecioMensual()).isEqualByComparingTo("109.00");
        }

        @Test
        @DisplayName("Estado TRIAL + legado (plan_codigo null) → suscripción completada con env var")
        void trialLegado_suscribeConEnvVar() {
            Suscripcion trial = suscripcionEnEstado("TRIAL", null);
            when(suscripcionRepository.findFirstByTenantIdOrderByIdDesc(TENANT_ID))
                    .thenReturn(Optional.of(trial));
            when(suscripcionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            controller.suscribir(request("BASICO", "tok_trial_legacy"));

            verify(culqiService).crearSuscripcion(eq("crd_test"), eq("plan_legacy_basico"), any());
        }
    }

    // ── Suscribir — catálogo ─────────────────────────────────────────────────

    @Nested
    @DisplayName("suscribir — ruta catálogo")
    class SuscribirCatalogo {

        @Test
        @DisplayName("Veterinaria BASICO (S/109) — usa culqi_plan_id del catálogo, no env var")
        void veterinariaBasico_usaCatalogoPlanId() {
            Suscripcion pendiente = suscripcionEnEstado("PENDIENTE", "VETERINARIA_BASICO");
            when(suscripcionRepository.findFirstByTenantIdOrderByIdDesc(TENANT_ID))
                    .thenReturn(Optional.of(pendiente));
            when(suscripcionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PlanCatalogo vetBasico = planCatalogo("VETERINARIA_BASICO", new BigDecimal("109.00"), "plan_culqi_vet_basico");
            when(planCatalogoRepository.findByCodigoAndActivoTrue("VETERINARIA_BASICO"))
                    .thenReturn(Optional.of(vetBasico));

            controller.suscribir(request("BASICO", "tok_vet"));

            verify(culqiService).crearSuscripcion(eq("crd_test"), eq("plan_culqi_vet_basico"), any());
        }

        @Test
        @DisplayName("Veterinaria PRO (S/189) — cambia nivel BASICO→PRO y usa culqi_plan_id correcto")
        void veterinariaPro_cambiaANivelPro() {
            Suscripcion pendiente = suscripcionEnEstado("PENDIENTE", "VETERINARIA_BASICO");
            when(suscripcionRepository.findFirstByTenantIdOrderByIdDesc(TENANT_ID))
                    .thenReturn(Optional.of(pendiente));
            when(suscripcionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PlanCatalogo vetPro = planCatalogo("VETERINARIA_PRO", new BigDecimal("189.00"), "plan_culqi_vet_pro");
            when(planCatalogoRepository.findByCodigoAndActivoTrue("VETERINARIA_PRO"))
                    .thenReturn(Optional.of(vetPro));

            controller.suscribir(request("PRO", "tok_vet_pro"));

            verify(culqiService).crearSuscripcion(eq("crd_test"), eq("plan_culqi_vet_pro"), any());
        }

        @Test
        @DisplayName("Plan sin culqi_plan_id configurado → BadRequestException sin crear suscripción en Culqi")
        void planSinCulqiPlanId_lanzaSinCobrar() {
            Suscripcion pendiente = suscripcionEnEstado("PENDIENTE", "VETERINARIA_BASICO");
            when(suscripcionRepository.findFirstByTenantIdOrderByIdDesc(TENANT_ID))
                    .thenReturn(Optional.of(pendiente));

            PlanCatalogo vetSinId = planCatalogo("VETERINARIA_BASICO", new BigDecimal("109.00"), null);
            when(planCatalogoRepository.findByCodigoAndActivoTrue("VETERINARIA_BASICO"))
                    .thenReturn(Optional.of(vetSinId));

            assertThatThrownBy(() -> controller.suscribir(request("BASICO", "tok_test")))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("culqi_plan_id");

            verify(culqiService, never()).crearSuscripcion(any(), any(), any());
        }
    }

    // ── Suscribir — ruta legada ──────────────────────────────────────────────

    @Nested
    @DisplayName("suscribir — ruta legada (plan_codigo NULL)")
    class SuscribirLegado {

        @Test
        @DisplayName("plan_codigo NULL → usa variable de entorno CULQI_PLAN_ID_BASICO")
        void planCodigoNull_usaEnvVar() {
            Suscripcion pendiente = suscripcionEnEstado("PENDIENTE", null); // sin plan_codigo
            when(suscripcionRepository.findFirstByTenantIdOrderByIdDesc(TENANT_ID))
                    .thenReturn(Optional.of(pendiente));
            when(suscripcionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            controller.suscribir(request("BASICO", "tok_legacy"));

            verify(culqiService).crearSuscripcion(eq("crd_test"), eq("plan_legacy_basico"), any());
            verify(planCatalogoRepository, never()).findByCodigoAndActivoTrue(any());
        }

        @Test
        @DisplayName("Sin suscripción previa → también usa env var (tenant completamente nuevo)")
        void sinSuscripcionPrevia_usaEnvVar() {
            when(suscripcionRepository.findFirstByTenantIdOrderByIdDesc(TENANT_ID))
                    .thenReturn(Optional.empty());
            when(suscripcionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            controller.suscribir(request("BASICO", "tok_nuevo"));

            verify(culqiService).crearSuscripcion(eq("crd_test"), eq("plan_legacy_basico"), any());
        }
    }

    // ── Compatibilidad legado con plan_codigo NULL ───────────────────────────

    @Nested
    @DisplayName("Compatibilidad legada — plan_codigo NULL")
    class CompatibilidadLegado {

        @Test
        @DisplayName("upgradePro con plan_codigo NULL → usa env var CULQI_PLAN_ID_PRO")
        void upgradePro_legado_usaEnvVar() {
            Suscripcion activa = suscripcionEnEstado("ACTIVA", null);
            activa.setPreapprovalId("sub_basico_old");
            when(suscripcionRepository.findFirstByTenantIdOrderByIdDesc(TENANT_ID))
                    .thenReturn(Optional.of(activa));
            when(suscripcionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            assertThatNoException().isThrownBy(() -> controller.upgradePro(request("PRO", "tok_up")));
            verify(culqiService, atLeastOnce()).crearSuscripcion(any(), eq("plan_legacy_pro"), any());
        }

        @Test
        @DisplayName("Plan destino sin culqi_plan_id → BadRequestException sin llamadas a Culqi")
        void planSinCulqiPlanId_lanzaEnUpgrade() {
            Suscripcion activa = suscripcionEnEstado("ACTIVA", "VETERINARIA_BASICO");
            when(suscripcionRepository.findFirstByTenantIdOrderByIdDesc(TENANT_ID))
                    .thenReturn(Optional.of(activa));
            // VETERINARIA_PRO sin culqi_plan_id
            PlanCatalogo vetProSinId = planCatalogo("VETERINARIA_PRO", new BigDecimal("189.00"), null);
            when(planCatalogoRepository.findByCodigoAndActivoTrue("VETERINARIA_PRO"))
                    .thenReturn(Optional.of(vetProSinId));
            // VETERINARIA_BASICO para el pre-cálculo de recuperación
            when(planCatalogoRepository.findByCodigoAndActivoTrue("VETERINARIA_BASICO"))
                    .thenReturn(Optional.of(planCatalogo("VETERINARIA_BASICO", new BigDecimal("109.00"), "plan_vet_bas")));

            assertThatThrownBy(() -> controller.upgradePro(request("PRO", "tok_up")))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("culqi_plan_id");

            verify(culqiService, never()).crearCliente(any(), any(), any(), any());
        }
    }

    // ── Bug 1 — Reactivación tras downgrade fallido ──────────────────────────

    @Nested
    @DisplayName("Bug 1 — Reactivación tras downgrade con cobro fallido")
    class ReactivacionTrasDowngradeFallido {

        @Test
        @DisplayName("SUSPENDIDA + preapprovalId → cancela sub Culqi anterior antes de crear la nueva")
        void suspendida_conPreapprovalId_cancelaAntesDeCrear() {
            Suscripcion suspendida = suscripcionEnEstado("SUSPENDIDA", null);
            suspendida.setPreapprovalId("sub_basico_fallido_001");
            when(suscripcionRepository.findFirstByTenantIdOrderByIdDesc(TENANT_ID))
                    .thenReturn(Optional.of(suspendida));
            when(suscripcionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            controller.suscribir(request("BASICO", "tok_reactivar"));

            var inOrder = inOrder(culqiService);
            inOrder.verify(culqiService).cancelarSuscripcion("sub_basico_fallido_001");
            inOrder.verify(culqiService).crearSuscripcion(any(), any(), any());
        }

        @Test
        @DisplayName("SUSPENDIDA + preapprovalId + cancelar falla → continúa y crea nueva suscripción")
        void suspendida_cancelarFalla_continuaYCrea() {
            Suscripcion suspendida = suscripcionEnEstado("SUSPENDIDA", null);
            suspendida.setPreapprovalId("sub_ya_expirada_002");
            when(suscripcionRepository.findFirstByTenantIdOrderByIdDesc(TENANT_ID))
                    .thenReturn(Optional.of(suspendida));
            when(suscripcionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            doThrow(new RuntimeException("sub not found")).when(culqiService)
                    .cancelarSuscripcion("sub_ya_expirada_002");

            var resp = controller.suscribir(request("BASICO", "tok_reactivar_2"));

            verify(culqiService).crearSuscripcion(any(), any(), any());
            assertThat(resp.getBody().getEstado()).isEqualTo("ACTIVA");
        }

        @Test
        @DisplayName("SUSPENDIDA sin preapprovalId → NO llama cancelarSuscripcion (trial expirado sin sub Culqi)")
        void suspendida_sinPreapprovalId_noCancela() {
            Suscripcion suspendida = suscripcionEnEstado("SUSPENDIDA", null);
            suspendida.setPreapprovalId(null);
            when(suscripcionRepository.findFirstByTenantIdOrderByIdDesc(TENANT_ID))
                    .thenReturn(Optional.of(suspendida));
            when(suscripcionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            controller.suscribir(request("BASICO", "tok_sin_sub"));

            verify(culqiService, never()).cancelarSuscripcion(any());
            verify(culqiService).crearSuscripcion(any(), any(), any());
        }

        @Test
        @DisplayName("ACTIVA → rechazada con BadRequestException (no se modifica estado)")
        void activa_rechazadaSinCambiarEstado() {
            Suscripcion activa = suscripcionEnEstado("ACTIVA", null);
            activa.setPreapprovalId("sub_activa_003");
            when(suscripcionRepository.findFirstByTenantIdOrderByIdDesc(TENANT_ID))
                    .thenReturn(Optional.of(activa));

            assertThatThrownBy(() -> controller.suscribir(request("BASICO", "tok_activa")))
                    .isInstanceOf(BadRequestException.class);

            verify(culqiService, never()).cancelarSuscripcion(any());
            verify(culqiService, never()).crearSuscripcion(any(), any(), any());
            verify(suscripcionRepository, never()).save(any());
        }

        @Test
        @DisplayName("crearSuscripcion falla → excepción propagada, estado local no queda ACTIVO")
        void crearFalla_estadoLocalNoQuedarActivo() {
            Suscripcion suspendida = suscripcionEnEstado("SUSPENDIDA", null);
            suspendida.setPreapprovalId("sub_basico_004");
            when(suscripcionRepository.findFirstByTenantIdOrderByIdDesc(TENANT_ID))
                    .thenReturn(Optional.of(suspendida));
            when(culqiService.crearSuscripcion(any(), any(), any()))
                    .thenThrow(new RuntimeException("Culqi timeout"));

            assertThatThrownBy(() -> controller.suscribir(request("BASICO", "tok_falla_culqi")))
                    .isInstanceOf(RuntimeException.class);

            verify(suscripcionRepository, never()).save(any());
            assertThat(suspendida.getEstado()).isEqualTo("SUSPENDIDA");
        }
    }

    // ── Helpers de test ──────────────────────────────────────────────────────

    private static Suscripcion suscripcionEnEstado(String estado, String planCodigo) {
        Suscripcion s = new Suscripcion();
        s.setId(1L);
        s.setTenantId(TENANT_ID);
        s.setEstado(estado);
        s.setPlanCodigo(planCodigo);
        s.setPlanId(planCodigo != null && planCodigo.endsWith("PRO") ? "PRO" : "BASICO");
        return s;
    }

    private static CulqiSuscribirRequestDTO request(String planId, String tokenId) {
        CulqiSuscribirRequestDTO r = new CulqiSuscribirRequestDTO();
        r.setPlanId(planId);
        r.setTokenId(tokenId);
        return r;
    }

    private static PlanCatalogo planCatalogo(String codigo, BigDecimal precio, String culqiPlanId) {
        return PlanCatalogo.builder()
                .codigo(codigo)
                .segmento("VETERINARIA")
                .rubroOperativo("VETERINARIA")
                .nivel(codigo.endsWith("PRO") ? "PRO" : "BASICO")
                .precioMensual(precio)
                .maxUsuarios(5).maxSucursales(2).maxProductos(1000)
                .activo(true)
                .culqiPlanId(culqiPlanId)
                .build();
    }
}
