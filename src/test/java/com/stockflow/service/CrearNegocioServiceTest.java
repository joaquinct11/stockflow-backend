package com.stockflow.service;

import com.stockflow.config.RolePermissionDefaults;
import com.stockflow.config.properties.CulqiProperties;
import com.stockflow.config.properties.JwtProperties;
import com.stockflow.dto.CrearNegocioRequestDTO;
import com.stockflow.dto.TenantInfoDTO;
import com.stockflow.entity.*;
import com.stockflow.exception.BadRequestException;
import com.stockflow.exception.UnauthorizedException;
import com.stockflow.repository.RolRepository;
import com.stockflow.repository.UsuarioRepository;
import com.stockflow.repository.UsuarioTenantRepository;
import com.stockflow.service.impl.AuthServiceImpl;
import com.stockflow.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("crearNegocio — agrega nuevo tenant al usuario autenticado")
class CrearNegocioServiceTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private UsuarioTenantRepository usuarioTenantRepository;
    @Mock private RolRepository rolRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @Mock private JwtProperties jwtProperties;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private TenantService tenantService;
    @Mock private SuscripcionService suscripcionService;
    @Mock private EmailService emailService;
    @Mock private UsuarioPermisoService usuarioPermisoService;
    @Mock private RolePermissionDefaults rolePermissionDefaults;
    @Mock private CulqiProperties culqiProperties;
    @Mock private SucursalService sucursalService;

    @InjectMocks
    private AuthServiceImpl authService;

    private static final Long USUARIO_ID = 10L;
    private static final String TENANT_NUEVO = "mi-negocio-a1b2";

    private Rol rolAdmin;
    private Usuario usuario;
    private Tenant tenantNuevo;

    @BeforeEach
    void setUp() {
        rolAdmin = Rol.builder().id(1L).nombre("ADMIN").build();

        usuario = Usuario.builder()
                .id(USUARIO_ID)
                .email("user@test.com")
                .nombre("Test User")
                .activo(true)
                .build();

        tenantNuevo = Tenant.builder()
                .id(20L)
                .tenantId(TENANT_NUEVO)
                .nombre("Mi Negocio")
                .rubro("TIENDA")
                .activo(true)
                .build();

        // Comunes
        when(usuarioRepository.findById(USUARIO_ID)).thenReturn(Optional.of(usuario));
        when(rolRepository.findByNombre("ADMIN")).thenReturn(Optional.of(rolAdmin));
        when(tenantService.crearTenant(any(), any(), any(), any(), any())).thenReturn(tenantNuevo);
        when(tenantService.obtenerTenant(TENANT_NUEVO)).thenReturn(Optional.of(tenantNuevo));
        when(culqiProperties.getPrecioBasico()).thenReturn(new BigDecimal("29.90"));
        when(culqiProperties.getPrecioPro()).thenReturn(new BigDecimal("59.90"));
        when(suscripcionService.crearSuscripcion(any())).thenAnswer(inv -> inv.getArgument(0));
        when(usuarioTenantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── 1. Caso feliz con plan BASICO ─────────────────────────────────────────

    @Test
    @DisplayName("Plan BASICO — crea tenant, usuario_tenant y suscripción TRIAL correctamente")
    void crearNegocio_planBasico_creaTodasLasEntidades() {
        CrearNegocioRequestDTO dto = CrearNegocioRequestDTO.builder()
                .nombreNegocio("Mi Negocio")
                .rubro("TIENDA")
                .ruc("12345678901")
                .planId("BASICO")
                .build();

        TenantInfoDTO result = authService.crearNegocio(USUARIO_ID, dto);

        assertThat(result).isNotNull();
        assertThat(result.getTenantId()).isEqualTo(TENANT_NUEVO);
        assertThat(result.getNombre()).isEqualTo("Mi Negocio");
        assertThat(result.getRol()).isEqualTo("ADMIN");
    }

    // ── 2. Se invoca crearTenant con los parámetros correctos ─────────────────

    @Test
    @DisplayName("Delega al TenantService con los datos exactos del request")
    void crearNegocio_delegaCorrectamenteATenantService() {
        CrearNegocioRequestDTO dto = CrearNegocioRequestDTO.builder()
                .nombreNegocio("Farmacia Central")
                .rubro("FARMACIA")
                .ruc("20501234567")
                .telefono("987654321")
                .emailContacto("contacto@farmacia.com")
                .planId("BASICO")
                .build();

        authService.crearNegocio(USUARIO_ID, dto);

        verify(tenantService).crearTenant(
                eq("Farmacia Central"),
                eq("FARMACIA"),
                eq("20501234567"),
                eq("contacto@farmacia.com"),
                eq("987654321")
        );
    }

    // ── 3. Se guarda la fila en usuario_tenant con rol ADMIN ──────────────────

    @Test
    @DisplayName("Crea la fila usuario_tenant con rol ADMIN y activo=true")
    void crearNegocio_guardaUsuarioTenantConRolAdmin() {
        CrearNegocioRequestDTO dto = CrearNegocioRequestDTO.builder()
                .nombreNegocio("Mi Negocio")
                .planId("BASICO")
                .build();

        authService.crearNegocio(USUARIO_ID, dto);

        ArgumentCaptor<UsuarioTenant> captor = ArgumentCaptor.forClass(UsuarioTenant.class);
        verify(usuarioTenantRepository).save(captor.capture());

        UsuarioTenant saved = captor.getValue();
        assertThat(saved.getTenantId()).isEqualTo(TENANT_NUEVO);
        assertThat(saved.getRol().getNombre()).isEqualTo("ADMIN");
        assertThat(saved.getActivo()).isTrue();
        assertThat(saved.getUsuario().getId()).isEqualTo(USUARIO_ID);
    }

    // ── 4. Se crea suscripción TRIAL con el plan correcto ─────────────────────

    @Test
    @DisplayName("Crea suscripción TRIAL con planId BASICO y precio correcto")
    void crearNegocio_creaSuscripcionTrialBasico() {
        CrearNegocioRequestDTO dto = CrearNegocioRequestDTO.builder()
                .nombreNegocio("Mi Negocio")
                .planId("BASICO")
                .build();

        authService.crearNegocio(USUARIO_ID, dto);

        ArgumentCaptor<Suscripcion> captor = ArgumentCaptor.forClass(Suscripcion.class);
        verify(suscripcionService).crearSuscripcion(captor.capture());

        Suscripcion saved = captor.getValue();
        assertThat(saved.getEstado()).isEqualTo("TRIAL");
        assertThat(saved.getPlanId()).isEqualTo("BASICO");
        assertThat(saved.getPrecioMensual()).isEqualByComparingTo("29.90");
        assertThat(saved.getTenantId()).isEqualTo(TENANT_NUEVO);
        assertThat(saved.getTrialEndDate()).isAfter(saved.getFechaInicio());
    }

    // ── 5. Plan PRO — inicializa sucursal principal ───────────────────────────

    @Test
    @DisplayName("Plan PRO — invoca sucursalService.inicializarPrincipal")
    void crearNegocio_planPro_inicializaSucursal() {
        CrearNegocioRequestDTO dto = CrearNegocioRequestDTO.builder()
                .nombreNegocio("Mi Negocio")
                .planId("PRO")
                .build();

        authService.crearNegocio(USUARIO_ID, dto);

        verify(sucursalService).inicializarPrincipal(TENANT_NUEVO);
    }

    // ── 6. Plan BASICO — NO inicializa sucursal ───────────────────────────────

    @Test
    @DisplayName("Plan BASICO — NO invoca sucursalService.inicializarPrincipal")
    void crearNegocio_planBasico_noInicializaSucursal() {
        CrearNegocioRequestDTO dto = CrearNegocioRequestDTO.builder()
                .nombreNegocio("Mi Negocio")
                .planId("BASICO")
                .build();

        authService.crearNegocio(USUARIO_ID, dto);

        verify(sucursalService, never()).inicializarPrincipal(any());
    }

    // ── 7. Plan inválido lanza BadRequestException ────────────────────────────

    @Test
    @DisplayName("Plan inválido lanza BadRequestException")
    void crearNegocio_planInvalido_lanzaBadRequest() {
        CrearNegocioRequestDTO dto = CrearNegocioRequestDTO.builder()
                .nombreNegocio("Mi Negocio")
                .planId("PREMIUM_ULTRA")
                .build();

        assertThatThrownBy(() -> authService.crearNegocio(USUARIO_ID, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Plan inválido");

        verify(tenantService, never()).crearTenant(any(), any(), any(), any(), any());
    }

    // ── 8. Usuario no encontrado lanza UnauthorizedException ─────────────────

    @Test
    @DisplayName("Usuario no encontrado lanza UnauthorizedException")
    void crearNegocio_usuarioNoExiste_lanzaUnauthorized() {
        when(usuarioRepository.findById(USUARIO_ID)).thenReturn(Optional.empty());

        CrearNegocioRequestDTO dto = CrearNegocioRequestDTO.builder()
                .nombreNegocio("Mi Negocio")
                .planId("BASICO")
                .build();

        assertThatThrownBy(() -> authService.crearNegocio(USUARIO_ID, dto))
                .isInstanceOf(UnauthorizedException.class);

        verify(tenantService, never()).crearTenant(any(), any(), any(), any(), any());
    }

    // ── 9. Rol ADMIN no encontrado lanza BadRequestException ─────────────────

    @Test
    @DisplayName("Rol ADMIN no encontrado lanza BadRequestException")
    void crearNegocio_rolAdminNoExiste_lanzaBadRequest() {
        when(rolRepository.findByNombre("ADMIN")).thenReturn(Optional.empty());

        CrearNegocioRequestDTO dto = CrearNegocioRequestDTO.builder()
                .nombreNegocio("Mi Negocio")
                .planId("BASICO")
                .build();

        assertThatThrownBy(() -> authService.crearNegocio(USUARIO_ID, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ADMIN");
    }

    // ── 10. Campos opcionales null no impiden la creación ─────────────────────

    @Test
    @DisplayName("Campos opcionales null (ruc, telefono, emailContacto) no impiden la creación")
    void crearNegocio_camposOpcionalesNull_funciona() {
        CrearNegocioRequestDTO dto = CrearNegocioRequestDTO.builder()
                .nombreNegocio("Solo Nombre")
                .planId("BASICO")
                .build();

        TenantInfoDTO result = authService.crearNegocio(USUARIO_ID, dto);

        assertThat(result).isNotNull();
        verify(tenantService).crearTenant(eq("Solo Nombre"), isNull(), isNull(), isNull(), isNull());
    }

    // ── 11. Retorna TenantInfoDTO con datos del tenant creado ─────────────────

    @Test
    @DisplayName("Retorna TenantInfoDTO con tenantId, nombre y rubro del tenant creado")
    void crearNegocio_retornaTenantInfoDTO_correcto() {
        CrearNegocioRequestDTO dto = CrearNegocioRequestDTO.builder()
                .nombreNegocio("Mi Negocio")
                .rubro("TIENDA")
                .planId("BASICO")
                .build();

        TenantInfoDTO result = authService.crearNegocio(USUARIO_ID, dto);

        assertThat(result.getTenantId()).isEqualTo(TENANT_NUEVO);
        assertThat(result.getNombre()).isEqualTo("Mi Negocio");
        assertThat(result.getRubro()).isEqualTo("TIENDA");
        assertThat(result.getRol()).isEqualTo("ADMIN");
    }

    // ── 12. La suscripción tiene trialEndDate 14 días después ─────────────────

    @Test
    @DisplayName("La suscripción TRIAL expira 14 días después de la creación")
    void crearNegocio_trialEndDateEs14DiasDesp() {
        CrearNegocioRequestDTO dto = CrearNegocioRequestDTO.builder()
                .nombreNegocio("Mi Negocio")
                .planId("BASICO")
                .build();

        authService.crearNegocio(USUARIO_ID, dto);

        ArgumentCaptor<Suscripcion> captor = ArgumentCaptor.forClass(Suscripcion.class);
        verify(suscripcionService).crearSuscripcion(captor.capture());

        Suscripcion saved = captor.getValue();
        long diasDiff = java.time.temporal.ChronoUnit.DAYS.between(saved.getFechaInicio(), saved.getTrialEndDate());
        assertThat(diasDiff).isEqualTo(14);
    }

    // ── 13. Plan PRO — precio PRO en la suscripción ───────────────────────────

    @Test
    @DisplayName("Plan PRO — suscripción se crea con el precio PRO")
    void crearNegocio_planPro_usaPrecioPro() {
        CrearNegocioRequestDTO dto = CrearNegocioRequestDTO.builder()
                .nombreNegocio("Mi Negocio Pro")
                .planId("PRO")
                .build();

        authService.crearNegocio(USUARIO_ID, dto);

        ArgumentCaptor<Suscripcion> captor = ArgumentCaptor.forClass(Suscripcion.class);
        verify(suscripcionService).crearSuscripcion(captor.capture());

        assertThat(captor.getValue().getPrecioMensual()).isEqualByComparingTo("59.90");
    }
}
