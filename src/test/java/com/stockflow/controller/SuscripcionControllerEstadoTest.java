package com.stockflow.controller;

import com.stockflow.config.properties.CulqiProperties;
import com.stockflow.entity.Suscripcion;
import com.stockflow.mapper.SuscripcionMapper;
import com.stockflow.repository.ProductoRepository;
import com.stockflow.repository.SucursalRepository;
import com.stockflow.repository.SuscripcionRepository;
import com.stockflow.repository.UsuarioRepository;
import com.stockflow.repository.UsuarioTenantRepository;
import com.stockflow.service.CulqiService;
import com.stockflow.service.EmailService;
import com.stockflow.service.SuscripcionService;
import com.stockflow.service.UsuarioService;
import com.stockflow.dto.SuscripcionEstadoResponseDTO;
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
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios para GET /suscripciones/estado.
 * Verifica que precioMensual y planCodigo se devuelvan correctamente
 * tanto para suscripciones de catálogo como para legado.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SuscripcionController — GET /estado")
class SuscripcionControllerEstadoTest {

    @Mock private SuscripcionService     suscripcionService;
    @Mock private UsuarioService         usuarioService;
    @Mock private SuscripcionMapper      suscripcionMapper;
    @Mock private SuscripcionRepository  suscripcionRepository;
    @Mock private SucursalRepository     sucursalRepository;
    @Mock private ProductoRepository     productoRepository;
    @Mock private UsuarioRepository      usuarioRepository;
    @Mock private UsuarioTenantRepository usuarioTenantRepository;
    @Mock private CulqiService           culqiService;
    @Mock private EmailService           emailService;
    @Mock private CulqiProperties        culqiProperties;

    @InjectMocks
    private SuscripcionController controller;

    private MockedStatic<TenantContext> tenantContextMock;

    private static final String TENANT_ID  = "vet-fonavi-27f0";
    private static final Long   USUARIO_ID = 1L;

    @BeforeEach
    void setUp() {
        tenantContextMock = mockStatic(TenantContext.class);
        tenantContextMock.when(TenantContext::getCurrentTenant).thenReturn(TENANT_ID);
        tenantContextMock.when(TenantContext::getCurrentUserId).thenReturn(USUARIO_ID);

        // Legado env-var defaults
        when(culqiProperties.getPrecioBasico()).thenReturn(new BigDecimal("89.00"));
        when(culqiProperties.getPrecioPro()).thenReturn(new BigDecimal("169.00"));
    }

    @AfterEach
    void tearDown() {
        tenantContextMock.close();
    }

    // ── Catálogo ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Suscripciones de catálogo (planCodigo != null)")
    class Catalogo {

        @Test
        @DisplayName("VETERINARIA_BASICO → precioMensual=109, no usa env var")
        void veterinariaBasico_retorna109() {
            Suscripcion sus = suscripcion("TRIAL", "BASICO", "VETERINARIA_BASICO", new BigDecimal("109.00"));
            when(suscripcionRepository.findByTenantIdAndUsuarioPrincipalId(TENANT_ID, USUARIO_ID))
                    .thenReturn(Optional.of(sus));

            ResponseEntity<SuscripcionEstadoResponseDTO> resp = controller.obtenerEstado();

            assertThat(resp.getStatusCodeValue()).isEqualTo(200);
            SuscripcionEstadoResponseDTO dto = resp.getBody();
            assertThat(dto).isNotNull();
            assertThat(dto.getPrecioMensual()).isEqualByComparingTo("109.00");
            assertThat(dto.getPlanCodigo()).isEqualTo("VETERINARIA_BASICO");
            assertThat(dto.getPlanId()).isEqualTo("BASICO");

            // Los env vars no deben haberse consultado para suscripciones de catálogo
            verify(culqiProperties, never()).getPrecioBasico();
            verify(culqiProperties, never()).getPrecioPro();
        }

        @Test
        @DisplayName("VETERINARIA_PRO → precioMensual=189, no usa env var")
        void veterinariaPro_retorna189() {
            Suscripcion sus = suscripcion("ACTIVA", "PRO", "VETERINARIA_PRO", new BigDecimal("189.00"));
            when(suscripcionRepository.findByTenantIdAndUsuarioPrincipalId(TENANT_ID, USUARIO_ID))
                    .thenReturn(Optional.of(sus));

            SuscripcionEstadoResponseDTO dto = controller.obtenerEstado().getBody();

            assertThat(dto.getPrecioMensual()).isEqualByComparingTo("189.00");
            assertThat(dto.getPlanCodigo()).isEqualTo("VETERINARIA_PRO");
            verify(culqiProperties, never()).getPrecioPro();
        }

        @Test
        @DisplayName("BOTICA_BASICO → precioMensual=89 del catálogo, planCodigo correcto")
        void boticaBasico_retorna89CatalogoPrecio() {
            Suscripcion sus = suscripcion("TRIAL", "BASICO", "BOTICA_BASICO", new BigDecimal("89.00"));
            when(suscripcionRepository.findByTenantIdAndUsuarioPrincipalId(TENANT_ID, USUARIO_ID))
                    .thenReturn(Optional.of(sus));

            SuscripcionEstadoResponseDTO dto = controller.obtenerEstado().getBody();

            assertThat(dto.getPrecioMensual()).isEqualByComparingTo("89.00");
            assertThat(dto.getPlanCodigo()).isEqualTo("BOTICA_BASICO");
            verify(culqiProperties, never()).getPrecioBasico();
        }

        @Test
        @DisplayName("TIENDA_BODEGA_BASICO → precioMensual=79 del catálogo")
        void tiendaBodegaBasico_retorna79() {
            Suscripcion sus = suscripcion("TRIAL", "BASICO", "TIENDA_BODEGA_BASICO", new BigDecimal("79.00"));
            when(suscripcionRepository.findByTenantIdAndUsuarioPrincipalId(TENANT_ID, USUARIO_ID))
                    .thenReturn(Optional.of(sus));

            SuscripcionEstadoResponseDTO dto = controller.obtenerEstado().getBody();

            assertThat(dto.getPrecioMensual()).isEqualByComparingTo("79.00");
            assertThat(dto.getPlanCodigo()).isEqualTo("TIENDA_BODEGA_BASICO");
        }
    }

    // ── Legado ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Suscripciones legado (planCodigo == null)")
    class Legado {

        @Test
        @DisplayName("planCodigo null + planId BASICO → usa env var 89.00")
        void legadoBasico_usaEnvVar89() {
            Suscripcion sus = suscripcion("ACTIVA", "BASICO", null, new BigDecimal("79.00"));
            when(suscripcionRepository.findByTenantIdAndUsuarioPrincipalId(TENANT_ID, USUARIO_ID))
                    .thenReturn(Optional.of(sus));

            SuscripcionEstadoResponseDTO dto = controller.obtenerEstado().getBody();

            assertThat(dto.getPrecioMensual()).isEqualByComparingTo("89.00");
            assertThat(dto.getPlanCodigo()).isNull();
            verify(culqiProperties).getPrecioBasico();
        }

        @Test
        @DisplayName("planCodigo null + planId PRO → usa env var 169.00")
        void legadoPro_usaEnvVar169() {
            Suscripcion sus = suscripcion("ACTIVA", "PRO", null, new BigDecimal("149.00"));
            when(suscripcionRepository.findByTenantIdAndUsuarioPrincipalId(TENANT_ID, USUARIO_ID))
                    .thenReturn(Optional.of(sus));

            SuscripcionEstadoResponseDTO dto = controller.obtenerEstado().getBody();

            assertThat(dto.getPrecioMensual()).isEqualByComparingTo("169.00");
            verify(culqiProperties).getPrecioPro();
        }

        @Test
        @DisplayName("Sin suscripción → estado SIN_SUSCRIPCION, sin llamar a env vars")
        void sinSuscripcion_retornaSinSuscripcion() {
            when(suscripcionRepository.findByTenantIdAndUsuarioPrincipalId(TENANT_ID, USUARIO_ID))
                    .thenReturn(Optional.empty());
            when(suscripcionRepository.findFirstByTenantIdOrderByIdDesc(TENANT_ID))
                    .thenReturn(Optional.empty());

            SuscripcionEstadoResponseDTO dto = controller.obtenerEstado().getBody();

            assertThat(dto.getEstado()).isEqualTo("SIN_SUSCRIPCION");
            verify(culqiProperties, never()).getPrecioBasico();
        }
    }

    // ── Lazy flip TRIAL expirado (DEF-S1) ────────────────────────────────────

    @Nested
    @DisplayName("Lazy flip de TRIAL expirado → PENDIENTE")
    class TrialExpiradoLazyFlip {

        @Test
        @DisplayName("TRIAL con trialEndDate en el pasado → GET /estado devuelve PENDIENTE y persiste")
        void trialExpirado_devuelvePendiente() {
            Suscripcion sus = suscripcion("TRIAL", "BASICO", "VETERINARIA_BASICO", new BigDecimal("109.00"));
            sus.setTrialEndDate(LocalDateTime.now().minusDays(1));
            when(suscripcionRepository.findByTenantIdAndUsuarioPrincipalId(TENANT_ID, USUARIO_ID))
                    .thenReturn(Optional.of(sus));

            Suscripcion pendiente = suscripcion("PENDIENTE", "BASICO", "VETERINARIA_BASICO", new BigDecimal("109.00"));
            when(suscripcionService.expirarTrial(sus.getId())).thenReturn(pendiente);

            SuscripcionEstadoResponseDTO dto = controller.obtenerEstado().getBody();

            assertThat(dto.getEstado()).isEqualTo("PENDIENTE");
            verify(suscripcionService).expirarTrial(sus.getId());
        }

        @Test
        @DisplayName("TRIAL con trialEndDate en el futuro → no se hace flip, devuelve TRIAL")
        void trialVigente_devuelveTrial() {
            Suscripcion sus = suscripcion("TRIAL", "BASICO", "VETERINARIA_BASICO", new BigDecimal("109.00"));
            sus.setTrialEndDate(LocalDateTime.now().plusDays(3));
            when(suscripcionRepository.findByTenantIdAndUsuarioPrincipalId(TENANT_ID, USUARIO_ID))
                    .thenReturn(Optional.of(sus));

            SuscripcionEstadoResponseDTO dto = controller.obtenerEstado().getBody();

            assertThat(dto.getEstado()).isEqualTo("TRIAL");
            verify(suscripcionService, never()).expirarTrial(any());
        }

        @Test
        @DisplayName("TRIAL sin trialEndDate → no se hace flip, devuelve TRIAL")
        void trialSinFecha_devuelveTrial() {
            Suscripcion sus = suscripcion("TRIAL", "BASICO", null, new BigDecimal("89.00"));
            sus.setTrialEndDate(null);
            when(suscripcionRepository.findByTenantIdAndUsuarioPrincipalId(TENANT_ID, USUARIO_ID))
                    .thenReturn(Optional.of(sus));

            SuscripcionEstadoResponseDTO dto = controller.obtenerEstado().getBody();

            assertThat(dto.getEstado()).isEqualTo("TRIAL");
            verify(suscripcionService, never()).expirarTrial(any());
        }
    }

    // ── Aislamiento multi-tenant ──────────────────────────────────────────────

    @Nested
    @DisplayName("Aislamiento multi-tenant — precio no se mezcla entre tenants")
    class AislamientoMultiTenant {

        @Test
        @DisplayName("Tenant vet usa 109 y tenant botica usa 89 sin cruzarse")
        void dosTenants_preciosSeparados() {
            // Veterinaria
            Suscripcion susVet = suscripcion("TRIAL", "BASICO", "VETERINARIA_BASICO", new BigDecimal("109.00"));
            when(suscripcionRepository.findByTenantIdAndUsuarioPrincipalId("vet-001", USUARIO_ID))
                    .thenReturn(Optional.of(susVet));

            // Botica
            Suscripcion susBot = suscripcion("TRIAL", "BASICO", "BOTICA_BASICO", new BigDecimal("89.00"));
            when(suscripcionRepository.findByTenantIdAndUsuarioPrincipalId("bot-002", USUARIO_ID))
                    .thenReturn(Optional.of(susBot));

            // Simular llamada como vet-001
            tenantContextMock.when(TenantContext::getCurrentTenant).thenReturn("vet-001");
            BigDecimal precioVet = controller.obtenerEstado().getBody().getPrecioMensual();
            assertThat(precioVet).isEqualByComparingTo("109.00");

            // Simular llamada como bot-002
            tenantContextMock.when(TenantContext::getCurrentTenant).thenReturn("bot-002");
            BigDecimal precioBot = controller.obtenerEstado().getBody().getPrecioMensual();
            assertThat(precioBot).isEqualByComparingTo("89.00");
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private static Suscripcion suscripcion(String estado, String planId, String planCodigo, BigDecimal precio) {
        Suscripcion s = new Suscripcion();
        s.setId(1L);
        s.setTenantId(TENANT_ID);
        s.setEstado(estado);
        s.setPlanId(planId);
        s.setPlanCodigo(planCodigo);
        s.setPrecioMensual(precio);
        return s;
    }
}
