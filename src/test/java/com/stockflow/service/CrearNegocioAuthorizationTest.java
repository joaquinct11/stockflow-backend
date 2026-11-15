package com.stockflow.service;

import com.stockflow.config.RolePermissionDefaults;
import com.stockflow.config.properties.CulqiProperties;
import com.stockflow.config.properties.JwtProperties;
import com.stockflow.dto.CrearNegocioRequestDTO;
import com.stockflow.entity.*;
import com.stockflow.exception.ForbiddenException;
import com.stockflow.repository.RolRepository;
import com.stockflow.repository.UsuarioRepository;
import com.stockflow.repository.UsuarioTenantRepository;
import com.stockflow.service.impl.AuthServiceImpl;
import com.stockflow.util.JwtUtil;
import com.stockflow.util.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pruebas de autorización para crearNegocio().
 * Verifica que solo usuarios con rol ADMIN pueden crear tenants,
 * tanto desde ACCESS token como desde TENANT_SELECTION token.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("crearNegocio — autorización por rol")
class CrearNegocioAuthorizationTest {

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

    private static final Long   USUARIO_ID    = 10L;
    private static final String TENANT_ACTUAL = "tenant-origen-abc";

    private Rol rolAdmin;
    private Rol rolGerente;
    private Rol rolVendedor;
    private Rol rolGestorInventario;
    private Rol rolTecnicoServicio;
    private Usuario usuario;
    private Tenant tenantNuevo;

    private final CrearNegocioRequestDTO dtoBasico = CrearNegocioRequestDTO.builder()
            .nombreNegocio("Nuevo Negocio")
            .rubro("TIENDA")
            .planId("BASICO")
            .build();

    @BeforeEach
    void setUp() {
        rolAdmin             = Rol.builder().id(1L).nombre("ADMIN").build();
        rolGerente           = Rol.builder().id(2L).nombre("GERENTE").build();
        rolVendedor          = Rol.builder().id(3L).nombre("VENDEDOR").build();
        rolGestorInventario  = Rol.builder().id(4L).nombre("GESTOR_INVENTARIO").build();
        rolTecnicoServicio   = Rol.builder().id(5L).nombre("TECNICO_SERVICIO").build();

        usuario = Usuario.builder()
                .id(USUARIO_ID)
                .email("user@test.com")
                .nombre("Test User")
                .activo(true)
                .build();

        tenantNuevo = Tenant.builder()
                .id(20L)
                .tenantId("nuevo-tenant-xyz")
                .nombre("Nuevo Negocio")
                .rubro("TIENDA")
                .activo(true)
                .build();

        // Mocks comunes para el camino feliz
        when(usuarioRepository.findById(USUARIO_ID)).thenReturn(Optional.of(usuario));
        when(rolRepository.findByNombre("ADMIN")).thenReturn(Optional.of(rolAdmin));
        when(tenantService.crearTenant(any(), any(), any(), any(), any())).thenReturn(tenantNuevo);
        when(tenantService.obtenerTenant(any())).thenReturn(Optional.of(tenantNuevo));
        when(culqiProperties.getPrecioBasico()).thenReturn(new BigDecimal("29.90"));
        when(culqiProperties.getPrecioPro()).thenReturn(new BigDecimal("59.90"));
        when(suscripcionService.crearSuscripcion(any())).thenAnswer(inv -> inv.getArgument(0));
        when(usuarioTenantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void setAccessToken(String rol) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "user@test.com", null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + rol))
                )
        );
        TenantContext.setCurrentTenant(TENANT_ACTUAL);
    }

    private void setAccessTokenSinTenant(String rol) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "user@test.com", null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + rol))
                )
        );
        TenantContext.clear();
    }

    private void setSelectionToken() {
        // TENANT_SELECTION: sin rol, sin tenantId
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user@test.com", null, List.of())
        );
        TenantContext.clear();
    }

    private UsuarioTenant buildUt(Rol rol) {
        return UsuarioTenant.builder()
                .usuario(usuario)
                .tenantId(TENANT_ACTUAL)
                .rol(rol)
                .activo(true)
                .build();
    }

    // ── ACCESS token ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("ACCESS — ADMIN: permitido")
    void accessToken_admin_permite() {
        setAccessToken("ADMIN");
        when(usuarioTenantRepository.findByUsuarioIdAndTenantIdAndActivoTrue(USUARIO_ID, TENANT_ACTUAL))
                .thenReturn(Optional.of(buildUt(rolAdmin)));

        assertThatCode(() -> authService.crearNegocio(USUARIO_ID, dtoBasico))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ACCESS — GERENTE: 403 Forbidden")
    void accessToken_gerente_rechaza() {
        setAccessToken("GERENTE");

        assertThatThrownBy(() -> authService.crearNegocio(USUARIO_ID, dtoBasico))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("administradores");
    }

    @Test
    @DisplayName("ACCESS — VENDEDOR: 403 Forbidden")
    void accessToken_vendedor_rechaza() {
        setAccessToken("VENDEDOR");

        assertThatThrownBy(() -> authService.crearNegocio(USUARIO_ID, dtoBasico))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("administradores");
    }

    @Test
    @DisplayName("ACCESS — GESTOR_INVENTARIO (Almacenero): 403 Forbidden")
    void accessToken_gestorInventario_rechaza() {
        setAccessToken("GESTOR_INVENTARIO");

        assertThatThrownBy(() -> authService.crearNegocio(USUARIO_ID, dtoBasico))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("administradores");
    }

    @Test
    @DisplayName("ACCESS — TECNICO_SERVICIO: 403 Forbidden")
    void accessToken_tecnicoServicio_rechaza() {
        setAccessToken("TECNICO_SERVICIO");

        assertThatThrownBy(() -> authService.crearNegocio(USUARIO_ID, dtoBasico))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("administradores");
    }

    @Test
    @DisplayName("ACCESS — ADMIN con tenantActual null en contexto: 403 Forbidden")
    void accessToken_admin_conTenantNull_rechaza() {
        setAccessTokenSinTenant("ADMIN"); // ROLE_ADMIN pero TenantContext vacío

        assertThatThrownBy(() -> authService.crearNegocio(USUARIO_ID, dtoBasico))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("tenant");
    }

    @Test
    @DisplayName("ACCESS — ADMIN con tenantActual vacío en contexto: 403 Forbidden")
    void accessToken_admin_conTenantVacio_rechaza() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "user@test.com", null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                )
        );
        TenantContext.setCurrentTenant("   "); // blank, no null

        assertThatThrownBy(() -> authService.crearNegocio(USUARIO_ID, dtoBasico))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("tenant");
    }

    @Test
    @DisplayName("ACCESS — ADMIN pero usuario_tenant no existe: 403 Forbidden")
    void accessToken_admin_sinRelacionEnBd_rechaza() {
        setAccessToken("ADMIN");
        when(usuarioTenantRepository.findByUsuarioIdAndTenantIdAndActivoTrue(USUARIO_ID, TENANT_ACTUAL))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.crearNegocio(USUARIO_ID, dtoBasico))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("ACCESS — ADMIN token pero usuario_tenant tiene rol GERENTE en BD: 403 Forbidden")
    void accessToken_adminToken_peroRolGerenteEnBd_rechaza() {
        setAccessToken("ADMIN");
        when(usuarioTenantRepository.findByUsuarioIdAndTenantIdAndActivoTrue(USUARIO_ID, TENANT_ACTUAL))
                .thenReturn(Optional.of(buildUt(rolGerente)));

        assertThatThrownBy(() -> authService.crearNegocio(USUARIO_ID, dtoBasico))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("administradores");
    }

    // ── TENANT_SELECTION token ────────────────────────────────────────────────

    @Test
    @DisplayName("TENANT_SELECTION — usuario con relación ADMIN activa: permitido")
    void selectionToken_conRelacionAdmin_permite() {
        setSelectionToken();
        when(usuarioTenantRepository.existsByUsuario_IdAndRol_NombreAndActivoTrue(USUARIO_ID, "ADMIN"))
                .thenReturn(true);

        assertThatCode(() -> authService.crearNegocio(USUARIO_ID, dtoBasico))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("TENANT_SELECTION — usuario sin ninguna relación ADMIN: 403 Forbidden")
    void selectionToken_sinRelacionAdmin_rechaza() {
        setSelectionToken();
        when(usuarioTenantRepository.existsByUsuario_IdAndRol_NombreAndActivoTrue(USUARIO_ID, "ADMIN"))
                .thenReturn(false);

        assertThatThrownBy(() -> authService.crearNegocio(USUARIO_ID, dtoBasico))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("administradores");
    }

    @Test
    @DisplayName("TENANT_SELECTION — usuario solo VENDEDOR en todos sus tenants: 403 Forbidden")
    void selectionToken_soloVendedor_rechaza() {
        setSelectionToken();
        when(usuarioTenantRepository.existsByUsuario_IdAndRol_NombreAndActivoTrue(USUARIO_ID, "ADMIN"))
                .thenReturn(false);

        assertThatThrownBy(() -> authService.crearNegocio(USUARIO_ID, dtoBasico))
                .isInstanceOf(ForbiddenException.class);
    }

    // ── Sin autenticación ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Sin sesión activa: 403 Forbidden")
    void sinAutenticacion_rechaza() {
        // SecurityContextHolder sin autenticación (contexto vacío)
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> authService.crearNegocio(USUARIO_ID, dtoBasico))
                .isInstanceOf(ForbiddenException.class);
    }

    // ── Aislamiento entre usuarios ────────────────────────────────────────────

    @Test
    @DisplayName("Usuario autorizado no puede crear tenant para otro usuarioId")
    void admin_noPuedeSuplantarOtroUsuario() {
        Long otroUsuarioId = 99L;
        setAccessToken("ADMIN");
        when(usuarioRepository.findById(otroUsuarioId))
                .thenReturn(Optional.empty()); // ese usuario no existe
        when(usuarioTenantRepository.findByUsuarioIdAndTenantIdAndActivoTrue(USUARIO_ID, TENANT_ACTUAL))
                .thenReturn(Optional.of(buildUt(rolAdmin)));

        // La autorización del contexto usa USUARIO_ID, pero el controlador pasa el id del contexto
        // Este test verifica que el service lanza si el usuario del request no existe en BD
        assertThatThrownBy(() -> authService.crearNegocio(otroUsuarioId, dtoBasico))
                .isInstanceOf(Exception.class); // UnauthorizedException o ForbiddenException
    }
}
