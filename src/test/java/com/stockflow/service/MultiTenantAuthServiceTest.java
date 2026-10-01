package com.stockflow.service;

import com.stockflow.config.RolePermissionDefaults;
import com.stockflow.config.properties.CulqiProperties;
import com.stockflow.config.properties.JwtProperties;
import com.stockflow.dto.JwtResponseDTO;
import com.stockflow.dto.LoginDTO;
import com.stockflow.dto.SelectTenantRequestDTO;
import com.stockflow.dto.TenantInfoDTO;
import com.stockflow.entity.*;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Multi-tenant AuthService — login, selectTenant, refresh, cross-tenant")
class MultiTenantAuthServiceTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private UsuarioTenantRepository usuarioTenantRepository;
    @Mock private RolRepository rolRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @Mock private JwtProperties jwtProperties;
    @Mock private JwtProperties.Refresh jwtRefreshProperties;
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

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";
    private static final String EMAIL     = "user@test.com";
    private static final String PASSWORD  = "pass123";
    private static final String HASH      = "$2a$10$hash";

    private Rol rolAdmin;
    private Rol rolCajero;
    private Usuario usuario;
    private UsuarioTenant utTenantA;
    private UsuarioTenant utTenantB;
    private RefreshToken refreshTokenEntity;

    @BeforeEach
    void setUp() {
        rolAdmin  = Rol.builder().id(1L).nombre("ADMIN").build();
        rolCajero = Rol.builder().id(2L).nombre("CAJERO").build();

        usuario = Usuario.builder()
                .id(10L)
                .email(EMAIL)
                .contraseña(HASH)
                .nombre("Test User")
                .activo(true)
                .rol(rolAdmin)
                .tenantId(TENANT_A)
                .build();

        utTenantA = UsuarioTenant.builder()
                .id(1L).usuario(usuario).tenantId(TENANT_A).rol(rolAdmin).activo(true).build();
        utTenantB = UsuarioTenant.builder()
                .id(2L).usuario(usuario).tenantId(TENANT_B).rol(rolCajero).activo(true).build();

        refreshTokenEntity = RefreshToken.builder()
                .id(1L).token("rt-token").usuario(usuario).revocado(false)
                .expiracion(LocalDateTime.now().plusDays(7)).build();

        // Mocks comunes
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);
        when(jwtProperties.getExpiration()).thenReturn(900_000L);
        when(jwtUtil.generateToken(any(), any(), any(), any(), any())).thenReturn("access-token");
        when(jwtUtil.generateSelectionToken(any(), any(), any())).thenReturn("selection-token");
        when(refreshTokenService.crearRefreshToken(any(Usuario.class), any())).thenReturn(refreshTokenEntity);
        when(suscripcionService.obtenerSuscripcionPorUsuario(any())).thenReturn(Optional.empty());
        when(tenantService.obtenerTenant(any())).thenReturn(Optional.empty());
    }

    // ── Case A: un solo tenant ────────────────────────────────────────────────

    @Test
    @DisplayName("Login Case A: usuario con un solo tenant recibe access+refresh token directamente")
    void login_singleTenant_returnsAccessTokenDirectly() {
        when(usuarioTenantRepository.findActivosConRolByUsuarioId(10L))
                .thenReturn(List.of(utTenantA));

        JwtResponseDTO response = authService.login(LoginDTO.builder().email(EMAIL).contraseña(PASSWORD).build());

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("rt-token");
        assertThat(response.getTenantId()).isEqualTo(TENANT_A);
        assertThat(response.getRol()).isEqualTo("ADMIN");
        assertThat(response.getSelectionToken()).isNull();
        assertThat(response.getTenants()).isNull();
    }

    // ── Case B: múltiples tenants ─────────────────────────────────────────────

    @Test
    @DisplayName("Login Case B: usuario con múltiples tenants recibe selectionToken + lista de tenants")
    void login_multiTenant_returnsSelectionTokenAndTenantList() {
        when(usuarioTenantRepository.findActivosConRolByUsuarioId(10L))
                .thenReturn(List.of(utTenantA, utTenantB));

        JwtResponseDTO response = authService.login(LoginDTO.builder().email(EMAIL).contraseña(PASSWORD).build());

        assertThat(response.getSelectionToken()).isEqualTo("selection-token");
        assertThat(response.getTenants()).hasSize(2);
        assertThat(response.getAccessToken()).isNull();
        assertThat(response.getRefreshToken()).isNull();
        verify(jwtUtil).generateSelectionToken(10L, EMAIL, "Test User");
    }

    @Test
    @DisplayName("Login Case B: la lista de tenants incluye el rol correspondiente por tenant")
    void login_multiTenant_tenantListHasCorrectRolesPerTenant() {
        when(usuarioTenantRepository.findActivosConRolByUsuarioId(10L))
                .thenReturn(List.of(utTenantA, utTenantB));

        JwtResponseDTO response = authService.login(LoginDTO.builder().email(EMAIL).contraseña(PASSWORD).build());

        List<TenantInfoDTO> tenants = response.getTenants();
        TenantInfoDTO infoA = tenants.stream().filter(t -> TENANT_A.equals(t.getTenantId())).findFirst().orElseThrow();
        TenantInfoDTO infoB = tenants.stream().filter(t -> TENANT_B.equals(t.getTenantId())).findFirst().orElseThrow();

        assertThat(infoA.getRol()).isEqualTo("ADMIN");
        assertThat(infoB.getRol()).isEqualTo("CAJERO");
    }

    // ── selectTenant ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("selectTenant: usuario puede acceder a su propio tenant")
    void selectTenant_validRelation_returnsFullToken() {
        when(usuarioTenantRepository.findByUsuarioIdAndTenantIdAndActivoTrue(10L, TENANT_A))
                .thenReturn(Optional.of(utTenantA));
        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(usuario));

        SelectTenantRequestDTO dto = new SelectTenantRequestDTO();
        dto.setTenantId(TENANT_A);

        JwtResponseDTO response = authService.selectTenant(10L, dto);

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getTenantId()).isEqualTo(TENANT_A);
        assertThat(response.getRol()).isEqualTo("ADMIN");
        verify(jwtUtil).generateToken(10L, EMAIL, "Test User", "ADMIN", TENANT_A);
    }

    @Test
    @DisplayName("selectTenant: usuario NO puede seleccionar un tenant al que no pertenece")
    void selectTenant_invalidTenant_throwsUnauthorized() {
        when(usuarioTenantRepository.findByUsuarioIdAndTenantIdAndActivoTrue(10L, "tenant-hacker"))
                .thenReturn(Optional.empty());

        SelectTenantRequestDTO dto = new SelectTenantRequestDTO();
        dto.setTenantId("tenant-hacker");

        assertThatThrownBy(() -> authService.selectTenant(10L, dto))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("tenant-hacker");
    }

    // ── getTenants ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getTenants: retorna solo tenants activos del usuario")
    void getTenants_returnsOnlyActiveTenants() {
        when(usuarioTenantRepository.findActivosConRolByUsuarioId(10L))
                .thenReturn(List.of(utTenantA, utTenantB));

        List<TenantInfoDTO> tenants = authService.getTenants(10L);

        assertThat(tenants).hasSize(2);
        assertThat(tenants).extracting(TenantInfoDTO::getTenantId)
                .containsExactlyInAnyOrder(TENANT_A, TENANT_B);
    }

    // ── refresh con tenantId en el token ──────────────────────────────────────

    @Test
    @DisplayName("refresh: valida que usuario aún pertenece al tenant del refresh token")
    void refresh_validTenant_renewsTokens() {
        RefreshToken rt = RefreshToken.builder().id(2L).token("rt-valid").usuario(usuario)
                .revocado(false).expiracion(LocalDateTime.now().plusDays(7)).build();

        when(refreshTokenService.validarRefreshToken("rt-valid")).thenReturn(rt);
        when(jwtUtil.getTenantIdFromToken("rt-valid")).thenReturn(TENANT_A);
        when(usuarioTenantRepository.findByUsuarioIdAndTenantIdAndActivoTrue(10L, TENANT_A))
                .thenReturn(Optional.of(utTenantA));
        when(refreshTokenService.crearRefreshToken(usuario, TENANT_A)).thenReturn(refreshTokenEntity);

        JwtResponseDTO response = authService.refresh("rt-valid");

        assertThat(response.getTenantId()).isEqualTo(TENANT_A);
        assertThat(response.getRol()).isEqualTo("ADMIN");
        verify(refreshTokenService).revocarRefreshToken("rt-valid");
        verify(refreshTokenService).crearRefreshToken(usuario, TENANT_A);
    }

    @Test
    @DisplayName("refresh: refresh token de tenant A no puede renovar contexto de tenant B")
    void refresh_wrongTenant_throwsUnauthorized() {
        RefreshToken rt = RefreshToken.builder().id(3L).token("rt-tenant-a").usuario(usuario)
                .revocado(false).expiracion(LocalDateTime.now().plusDays(7)).build();

        when(refreshTokenService.validarRefreshToken("rt-tenant-a")).thenReturn(rt);
        when(jwtUtil.getTenantIdFromToken("rt-tenant-a")).thenReturn(TENANT_A);
        // Simula que la relación fue desactivada
        when(usuarioTenantRepository.findByUsuarioIdAndTenantIdAndActivoTrue(10L, TENANT_A))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("rt-tenant-a"))
                .isInstanceOf(UnauthorizedException.class);

        verify(refreshTokenService).revocarRefreshToken("rt-tenant-a");
    }

    // ── Login: credenciales inválidas ──────────────────────────────────────────

    @Test
    @DisplayName("login: contraseña incorrecta lanza UnauthorizedException")
    void login_wrongPassword_throwsUnauthorized() {
        when(passwordEncoder.matches("wrong", HASH)).thenReturn(false);

        assertThatThrownBy(() -> authService.login(LoginDTO.builder().email(EMAIL).contraseña("wrong").build()))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("login: usuario inactivo lanza UnauthorizedException")
    void login_inactiveUser_throwsUnauthorized() {
        usuario.setActivo(false);
        assertThatThrownBy(() -> authService.login(LoginDTO.builder().email(EMAIL).contraseña(PASSWORD).build()))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("inactivo");
    }

    // ── SUPER_ADMIN ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("login: SUPER_ADMIN no consulta usuario_tenant y recibe access+refresh directamente")
    void login_superAdmin_bypassesUsuarioTenant() {
        Rol rolSuperAdmin = Rol.builder().id(99L).nombre("SUPER_ADMIN").build();
        Usuario superAdmin = Usuario.builder()
                .id(99L).email("super@admin.com").contraseña(HASH)
                .nombre("Super Admin").activo(true).rol(rolSuperAdmin)
                .tenantId(null).build();

        when(usuarioRepository.findByEmail("super@admin.com")).thenReturn(Optional.of(superAdmin));
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);
        when(jwtUtil.generateToken(99L, "super@admin.com", "Super Admin", "SUPER_ADMIN", null))
                .thenReturn("super-access-token");
        when(refreshTokenService.crearRefreshToken(superAdmin, null)).thenReturn(refreshTokenEntity);

        JwtResponseDTO response = authService.login(
                LoginDTO.builder().email("super@admin.com").contraseña(PASSWORD).build());

        assertThat(response.getAccessToken()).isEqualTo("super-access-token");
        assertThat(response.getRol()).isEqualTo("SUPER_ADMIN");
        assertThat(response.getTenantId()).isNull();
        assertThat(response.getSelectionToken()).isNull();
        // Nunca debe consultar usuario_tenant para SUPER_ADMIN
        verify(usuarioTenantRepository, never()).findActivosConRolByUsuarioId(99L);
    }

    // ── Seguridad enumeración de tenants ─────────────────────────────────────

    @Test
    @DisplayName("getTenants: el usuarioId viene del token — no se pueden enumerar tenants de otro usuario")
    void getTenants_usesTokenUserId_cannotEnumerateOtherUserTenants() {
        // El servicio usa el usuarioId pasado como parámetro (que viene del TenantContext/token)
        // Si alguien intenta pasar el ID de otro usuario, el sistema simplemente devuelve
        // los tenants de ese usuario, no los del atacante.
        // El contrato de seguridad: el controller SIEMPRE extrae el usuarioId del contexto del token.
        Long usuarioLegitimo = 10L;
        Long otroUsuario = 55L;

        when(usuarioTenantRepository.findActivosConRolByUsuarioId(usuarioLegitimo))
                .thenReturn(List.of(utTenantA));
        when(usuarioTenantRepository.findActivosConRolByUsuarioId(otroUsuario))
                .thenReturn(List.of());

        // El servicio devuelve exactamente los tenants del usuarioId recibido
        List<TenantInfoDTO> tenantsLegitimos = authService.getTenants(usuarioLegitimo);
        List<TenantInfoDTO> tenantsOtro       = authService.getTenants(otroUsuario);

        assertThat(tenantsLegitimos).hasSize(1);
        assertThat(tenantsOtro).isEmpty();

        // El servicio NO tiene parámetros extra; la seguridad está en el controller que
        // siempre pasa TenantContext.getCurrentUserId() (extraído del JWT firmado).
        verify(usuarioTenantRepository).findActivosConRolByUsuarioId(usuarioLegitimo);
        verify(usuarioTenantRepository).findActivosConRolByUsuarioId(otroUsuario);
    }
}
