package com.stockflow.controller;

import com.stockflow.config.properties.CulqiProperties;
import com.stockflow.dto.CulqiSuscribirRequestDTO;
import com.stockflow.entity.Suscripcion;
import com.stockflow.entity.Usuario;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CulqiController — email tenant-scoped y metadata por tenant")
class CulqiControllerTenantScopedEmailTest {

    private static final String TENANT_ID   = "vet-59ba";
    private static final Long   USUARIO_ID  = 5L;
    private static final String EMAIL_REAL  = "user@gmail.com";
    private static final String CULQI_EMAIL = "user+vet-59ba@gmail.com";
    private static final String PLAN_ID_BASICO = "pln_test_basico";
    private static final String PLAN_ID_PRO    = "pln_test_pro";
    private static final String CARD_ID     = "crd_test_xxx";
    private static final String SUB_ID      = "sxn_test_xxx";
    private static final String CUSTOMER_ID = "cus_test_xxx";

    @Mock private CulqiService        culqiService;
    @Mock private CulqiProperties     culqiProperties;
    @Mock private SuscripcionService  suscripcionService;
    @Mock private SuscripcionRepository suscripcionRepository;
    @Mock private UsuarioService      usuarioService;
    @Mock private SucursalService     sucursalService;
    @Mock private EmailService        emailService;

    @InjectMocks private CulqiController controller;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(TENANT_ID);
        TenantContext.setCurrentUserId(USUARIO_ID);

        usuario = new Usuario();
        usuario.setId(USUARIO_ID);
        usuario.setEmail(EMAIL_REAL);
        usuario.setNombre("Joaquin");
        usuario.setApellido("Castillo");
        usuario.setNumeroCelular("999999999");

        when(usuarioService.obtenerUsuarioPorId(USUARIO_ID)).thenReturn(Optional.of(usuario));
        when(culqiProperties.getPlanIdBasico()).thenReturn(PLAN_ID_BASICO);
        when(culqiProperties.getPlanIdPro()).thenReturn(PLAN_ID_PRO);
        when(culqiProperties.getPrecioBasico()).thenReturn(BigDecimal.valueOf(89));
        when(culqiProperties.getPrecioPro()).thenReturn(BigDecimal.valueOf(129));
        when(culqiService.crearCliente(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(CUSTOMER_ID);
        when(culqiService.crearTarjeta(anyString(), anyString()))
                .thenReturn(Map.of("id", CARD_ID));
        when(culqiService.crearSuscripcion(anyString(), anyString(), any()))
                .thenReturn(SUB_ID);
        when(suscripcionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sucursalService.desbloquearSucursalesAdicionales(anyString())).thenReturn(0);
        when(sucursalService.bloquearSucursalesAdicionales(anyString())).thenReturn(0);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── suscribir() ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("suscribir() usa email tenant-scoped")
    class Suscribir {

        @BeforeEach
        void setUp() {
            when(suscripcionRepository.findFirstByTenantIdOrderByIdDesc(TENANT_ID))
                    .thenReturn(Optional.empty());
        }

        @Test
        @DisplayName("crearCliente recibe email tenant-scoped, no el email real")
        void crearCliente_recibeEmailTenantScoped() {
            CulqiSuscribirRequestDTO req = new CulqiSuscribirRequestDTO();
            req.setTokenId("tok_test_xxx");
            req.setPlanId("BASICO");

            controller.suscribir(req);

            verify(culqiService).crearCliente(eq(CULQI_EMAIL), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("crearSuscripcion recibe metadata con tenant_id y usuario_id")
        void crearSuscripcion_recibeMetadataConTenantYUsuario() {
            CulqiSuscribirRequestDTO req = new CulqiSuscribirRequestDTO();
            req.setTokenId("tok_test_xxx");
            req.setPlanId("BASICO");

            controller.suscribir(req);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, String>> metaCaptor = ArgumentCaptor.forClass(Map.class);
            verify(culqiService).crearSuscripcion(eq(CARD_ID), eq(PLAN_ID_BASICO), metaCaptor.capture());

            Map<String, String> meta = metaCaptor.getValue();
            assertThat(meta).containsEntry("tenant_id",  TENANT_ID);
            assertThat(meta).containsEntry("usuario_id", String.valueOf(USUARIO_ID));
        }
    }

    // ── upgradePro() ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("upgradePro() usa email tenant-scoped")
    class UpgradePro {

        @BeforeEach
        void setUp() {
            Suscripcion suscripcionBasico = Suscripcion.builder()
                    .id(1L).tenantId(TENANT_ID).planId("BASICO").estado("ACTIVA")
                    .preapprovalId("sxn_test_basico").build();
            when(suscripcionRepository.findFirstByTenantIdOrderByIdDesc(TENANT_ID))
                    .thenReturn(Optional.of(suscripcionBasico));
        }

        @Test
        @DisplayName("crearCliente recibe email tenant-scoped")
        void crearCliente_recibeEmailTenantScoped() {
            CulqiSuscribirRequestDTO req = new CulqiSuscribirRequestDTO();
            req.setTokenId("tok_test_upgrade");

            controller.upgradePro(req);

            verify(culqiService).crearCliente(eq(CULQI_EMAIL), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("crearSuscripcion recibe metadata con tenant_id y usuario_id")
        void crearSuscripcion_recibeMetadata() {
            CulqiSuscribirRequestDTO req = new CulqiSuscribirRequestDTO();
            req.setTokenId("tok_test_upgrade");

            controller.upgradePro(req);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, String>> metaCaptor = ArgumentCaptor.forClass(Map.class);
            verify(culqiService).crearSuscripcion(eq(CARD_ID), eq(PLAN_ID_PRO), metaCaptor.capture());

            Map<String, String> meta = metaCaptor.getValue();
            assertThat(meta).containsEntry("tenant_id",  TENANT_ID);
            assertThat(meta).containsEntry("usuario_id", String.valueOf(USUARIO_ID));
        }
    }

    // ── cambiarTarjeta() ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("cambiarTarjeta() usa email tenant-scoped")
    class CambiarTarjeta {

        @BeforeEach
        void setUp() {
            Suscripcion s = Suscripcion.builder()
                    .id(1L).tenantId(TENANT_ID).estado("ACTIVA")
                    .preapprovalId("sxn_test_activa").build();
            when(suscripcionRepository.findFirstByTenantIdOrderByIdDesc(TENANT_ID))
                    .thenReturn(Optional.of(s));
            doNothing().when(culqiService).actualizarTarjetaSuscripcion(anyString(), anyString());
        }

        @Test
        @DisplayName("crearCliente recibe email tenant-scoped")
        void crearCliente_recibeEmailTenantScoped() {
            controller.cambiarTarjeta(Map.of("tokenId", "tok_nueva_tarjeta"));

            verify(culqiService).crearCliente(eq(CULQI_EMAIL), anyString(), anyString(), anyString());
        }
    }

    // ── downgradeBasico() ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("downgradeBasico() usa email tenant-scoped")
    class DowngradeBasico {

        @BeforeEach
        void setUp() {
            Suscripcion suscripcionPro = Suscripcion.builder()
                    .id(1L).tenantId(TENANT_ID).planId("PRO").estado("ACTIVA")
                    .preapprovalId("sxn_test_pro").build();
            when(suscripcionRepository.findFirstByTenantIdOrderByIdDesc(TENANT_ID))
                    .thenReturn(Optional.of(suscripcionPro));
        }

        @Test
        @DisplayName("crearCliente recibe email tenant-scoped")
        void crearCliente_recibeEmailTenantScoped() {
            CulqiSuscribirRequestDTO req = new CulqiSuscribirRequestDTO();
            req.setTokenId("tok_test_downgrade");

            controller.downgradeBasico(req);

            verify(culqiService).crearCliente(eq(CULQI_EMAIL), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("crearSuscripcion recibe metadata con tenant_id y usuario_id")
        void crearSuscripcion_recibeMetadata() {
            CulqiSuscribirRequestDTO req = new CulqiSuscribirRequestDTO();
            req.setTokenId("tok_test_downgrade");

            controller.downgradeBasico(req);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, String>> metaCaptor = ArgumentCaptor.forClass(Map.class);
            verify(culqiService).crearSuscripcion(eq(CARD_ID), eq(PLAN_ID_BASICO), metaCaptor.capture());

            Map<String, String> meta = metaCaptor.getValue();
            assertThat(meta).containsEntry("tenant_id",  TENANT_ID);
            assertThat(meta).containsEntry("usuario_id", String.valueOf(USUARIO_ID));
        }
    }

    // ── Aislamiento multi-tenant ──────────────────────────────────────────────

    @Nested
    @DisplayName("emails tenant-scoped garantizan aislamiento entre tenants")
    class AislamientoMultiTenant {

        @Test
        @DisplayName("dos tenants del mismo usuario producen emails distintos → customers distintos")
        void dosTenants_mismoUsuario_emailsDistintos() {
            String emailBotica = CulqiController.buildCulqiEmail(EMAIL_REAL, "botica-596f");
            String emailVet    = CulqiController.buildCulqiEmail(EMAIL_REAL, "vet-59ba");
            assertThat(emailBotica).isNotEqualTo(emailVet);
        }

        @Test
        @DisplayName("email tenant-scoped normalizado vuelve al email real del usuario")
        void emailTenantScoped_normalizado_vuelveAlEmailReal() {
            String emailVet = CulqiController.buildCulqiEmail(EMAIL_REAL, TENANT_ID);
            String normalizado = CulqiController.normalizarEmailCulqi(emailVet);
            assertThat(normalizado).isEqualTo(EMAIL_REAL);
        }
    }
}
