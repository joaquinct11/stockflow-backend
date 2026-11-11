package com.stockflow.controller;

import com.stockflow.config.properties.CulqiProperties;
import com.stockflow.dto.SuscripcionDTO;
import com.stockflow.entity.Suscripcion;
import com.stockflow.entity.Usuario;
import com.stockflow.exception.BadRequestException;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios para POST /suscripciones (creación manual administrativa).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SuscripcionController — POST /suscripciones (crear)")
class SuscripcionControllerCrearTest {

    @Mock private SuscripcionService      suscripcionService;
    @Mock private UsuarioService          usuarioService;
    @Mock private SuscripcionMapper       suscripcionMapper;
    @Mock private SuscripcionRepository   suscripcionRepository;
    @Mock private SucursalRepository      sucursalRepository;
    @Mock private ProductoRepository      productoRepository;
    @Mock private UsuarioRepository       usuarioRepository;
    @Mock private UsuarioTenantRepository usuarioTenantRepository;
    @Mock private CulqiService            culqiService;
    @Mock private EmailService            emailService;
    @Mock private CulqiProperties         culqiProperties;

    @InjectMocks
    private SuscripcionController controller;

    private MockedStatic<TenantContext> tenantContextMock;

    private static final String TENANT_ID  = "tenant-abc";
    private static final Long   USUARIO_ID = 10L;

    @BeforeEach
    void setUp() {
        tenantContextMock = mockStatic(TenantContext.class);
        tenantContextMock.when(TenantContext::getCurrentTenant).thenReturn(TENANT_ID);
        tenantContextMock.when(TenantContext::getCurrentUserId).thenReturn(USUARIO_ID);
    }

    @AfterEach
    void tearDown() {
        tenantContextMock.close();
    }

    // ── DEF-S2: validación de duplicado con filtro de tenant ──────────────────

    @Nested
    @DisplayName("Validación de duplicado por tenant (DEF-S2)")
    class ValidacionDuplicadoMultiTenant {

        @Test
        @DisplayName("Usuario sin suscripción activa en este tenant → crea correctamente")
        void sinSuscripcionActiva_crea201() {
            SuscripcionDTO dto = new SuscripcionDTO();
            dto.setUsuarioPrincipalId(USUARIO_ID);
            dto.setPlanId("BASICO");

            Usuario usuario = new Usuario();
            usuario.setId(USUARIO_ID);

            when(usuarioService.obtenerUsuarioPorId(USUARIO_ID)).thenReturn(Optional.of(usuario));
            // Sin suscripción activa en este tenant
            when(suscripcionService.obtenerSuscripcionPorUsuarioYTenant(USUARIO_ID, TENANT_ID))
                    .thenReturn(Optional.empty());

            Suscripcion nuevaSus = new Suscripcion();
            nuevaSus.setId(1L);
            nuevaSus.setPlanId("BASICO");
            nuevaSus.setEstado("ACTIVA");
            nuevaSus.setTenantId(TENANT_ID);
            when(suscripcionMapper.toEntity(dto)).thenReturn(nuevaSus);
            when(suscripcionService.crearSuscripcion(any())).thenReturn(nuevaSus);

            SuscripcionDTO respuestaDTO = new SuscripcionDTO();
            respuestaDTO.setPlanId("BASICO");
            when(suscripcionMapper.toDTO(any(Suscripcion.class))).thenReturn(respuestaDTO);

            ResponseEntity<SuscripcionDTO> resp = controller.crear(dto);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            verify(suscripcionService).crearSuscripcion(any());
        }

        @Test
        @DisplayName("Usuario con suscripción ACTIVA en este tenant → BadRequestException")
        void conSuscripcionActivaEnTenant_lanzaBadRequest() {
            SuscripcionDTO dto = new SuscripcionDTO();
            dto.setUsuarioPrincipalId(USUARIO_ID);
            dto.setPlanId("BASICO");

            Usuario usuario = new Usuario();
            usuario.setId(USUARIO_ID);

            when(usuarioService.obtenerUsuarioPorId(USUARIO_ID)).thenReturn(Optional.of(usuario));

            Suscripcion existente = new Suscripcion();
            existente.setEstado("ACTIVA");
            existente.setTenantId(TENANT_ID);
            when(suscripcionService.obtenerSuscripcionPorUsuarioYTenant(USUARIO_ID, TENANT_ID))
                    .thenReturn(Optional.of(existente));

            assertThatThrownBy(() -> controller.crear(dto))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("suscripción activa");

            verify(suscripcionService, never()).crearSuscripcion(any());
        }

        @Test
        @DisplayName("Usuario con suscripción TRIAL (no ACTIVA) en tenant → permite crear")
        void conSuscripcionTrialEnTenant_permite() {
            SuscripcionDTO dto = new SuscripcionDTO();
            dto.setUsuarioPrincipalId(USUARIO_ID);
            dto.setPlanId("BASICO");

            Usuario usuario = new Usuario();
            usuario.setId(USUARIO_ID);

            when(usuarioService.obtenerUsuarioPorId(USUARIO_ID)).thenReturn(Optional.of(usuario));

            Suscripcion trial = new Suscripcion();
            trial.setEstado("TRIAL");
            trial.setTenantId(TENANT_ID);
            when(suscripcionService.obtenerSuscripcionPorUsuarioYTenant(USUARIO_ID, TENANT_ID))
                    .thenReturn(Optional.of(trial));

            Suscripcion nueva = new Suscripcion();
            nueva.setId(2L);
            nueva.setPlanId("BASICO");
            nueva.setEstado("ACTIVA");
            nueva.setTenantId(TENANT_ID);
            when(suscripcionMapper.toEntity(dto)).thenReturn(nueva);
            when(suscripcionService.crearSuscripcion(any())).thenReturn(nueva);

            SuscripcionDTO respuestaDTO = new SuscripcionDTO();
            when(suscripcionMapper.toDTO(any(Suscripcion.class))).thenReturn(respuestaDTO);

            ResponseEntity<SuscripcionDTO> resp = controller.crear(dto);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        @Test
        @DisplayName("Plan distinto de BASICO → BadRequestException (restricción existente)")
        void planNoBasico_lanzaBadRequest() {
            SuscripcionDTO dto = new SuscripcionDTO();
            dto.setUsuarioPrincipalId(USUARIO_ID);
            dto.setPlanId("PRO");

            Usuario usuario = new Usuario();
            usuario.setId(USUARIO_ID);

            when(usuarioService.obtenerUsuarioPorId(USUARIO_ID)).thenReturn(Optional.of(usuario));
            when(suscripcionService.obtenerSuscripcionPorUsuarioYTenant(USUARIO_ID, TENANT_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> controller.crear(dto))
                    .isInstanceOf(BadRequestException.class);

            verify(suscripcionService, never()).crearSuscripcion(any());
        }
    }
}
