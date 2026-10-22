package com.stockflow.service;

import com.stockflow.config.RolePermissionDefaults;
import com.stockflow.config.properties.CulqiProperties;
import com.stockflow.config.properties.JwtProperties;
import com.stockflow.dto.RegistrationRequestDTO;
import com.stockflow.entity.*;
import com.stockflow.repository.PlanCatalogoRepository;
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
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Verifica que registrar() derive el rubro_operativo correcto del catálogo
 * y lo pase a crearTenant — nunca lo tome del request cuando llega planCodigo.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Registration — mapeo planCodigo → rubro_operativo correcto")
class RegistrationRubroMappingTest {

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
    @Mock private PlanCatalogoRepository planCatalogoRepository;

    @InjectMocks
    private AuthServiceImpl authService;

    private Rol rolAdmin;
    private Usuario usuarioCreado;
    private Tenant tenantCreado;
    private Suscripcion suscripcionCreada;
    private RefreshToken refreshToken;

    @BeforeEach
    void setUp() {
        rolAdmin = Rol.builder().id(1L).nombre("ADMIN").build();

        usuarioCreado = Usuario.builder()
                .id(99L).email("test@test.com").activo(true).nombre("Test").contraseña("hash")
                .build();

        tenantCreado = Tenant.builder()
                .id(1L).tenantId("tenant-x").nombre("Mi Negocio").activo(true).rubro("OTRO")
                .build();

        suscripcionCreada = Suscripcion.builder()
                .id(1L).planId("BASICO").planCodigo("OTRO_BASICO")
                .precioMensual(new BigDecimal("89.00")).estado("TRIAL")
                .usuarioPrincipal(usuarioCreado)
                .tenantId("tenant-x")
                .build();

        refreshToken = RefreshToken.builder()
                .id(1L).token("rt").usuario(usuarioCreado).revocado(false)
                .expiracion(java.time.LocalDateTime.now().plusDays(7)).build();

        when(usuarioRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(rolRepository.findByNombre("ADMIN")).thenReturn(Optional.of(rolAdmin));
        when(usuarioRepository.save(any())).thenReturn(usuarioCreado);
        when(usuarioTenantRepository.save(any())).thenReturn(mock(UsuarioTenant.class));
        when(tenantService.crearTenant(any(), any(), any(), any(), any())).thenReturn(tenantCreado);
        when(suscripcionService.crearSuscripcion(any())).thenReturn(suscripcionCreada);
        when(jwtUtil.generateToken(any(), any(), any(), any(), any())).thenReturn("access-token");
        when(refreshTokenService.crearRefreshToken(any(), any())).thenReturn(refreshToken);
        when(culqiProperties.getPrecioBasico()).thenReturn(new BigDecimal("89.00"));
        when(culqiProperties.getPrecioPro()).thenReturn(new BigDecimal("169.00"));
    }

    // ── TIENDA_ROPA_BASICO → rubro_operativo = TIENDA_ROPA ──────────────────────

    @Test
    @DisplayName("TIENDA_ROPA_BASICO deriva rubro=TIENDA_ROPA, no TIENDA")
    void tiendaRopaBasico_derivaRubroTiendaRopa() {
        PlanCatalogo plan = PlanCatalogo.builder()
                .codigo("TIENDA_ROPA_BASICO").segmento("TIENDA_ROPA").rubroOperativo("TIENDA_ROPA")
                .nivel("BASICO").precioMensual(new BigDecimal("89.00"))
                .maxUsuarios(5).maxSucursales(1).maxProductos(2000).activo(true).build();

        when(planCatalogoRepository.findByCodigoAndActivoTrue("TIENDA_ROPA_BASICO"))
                .thenReturn(Optional.of(plan));

        authService.registrar(buildRequest("TIENDA_ROPA_BASICO", "BASICO"));

        verify(tenantService).crearTenant(any(), eq("TIENDA_ROPA"), any(), any(), any());
    }

    // ── TIENDA_BODEGA_BASICO → rubro_operativo = TIENDA (no TIENDA_BODEGA) ──────

    @Test
    @DisplayName("TIENDA_BODEGA_BASICO deriva rubro=TIENDA, nunca TIENDA_BODEGA")
    void tiendaBodegaBasico_derivaRubroTiendaNoSegmento() {
        PlanCatalogo plan = PlanCatalogo.builder()
                .codigo("TIENDA_BODEGA_BASICO").segmento("TIENDA_BODEGA").rubroOperativo("TIENDA")
                .nivel("BASICO").precioMensual(new BigDecimal("79.00"))
                .maxUsuarios(5).maxSucursales(1).maxProductos(2000).activo(true).build();

        when(planCatalogoRepository.findByCodigoAndActivoTrue("TIENDA_BODEGA_BASICO"))
                .thenReturn(Optional.of(plan));

        authService.registrar(buildRequest("TIENDA_BODEGA_BASICO", "BASICO"));

        verify(tenantService).crearTenant(any(), eq("TIENDA"), any(), any(), any());
        verify(tenantService, never()).crearTenant(any(), eq("TIENDA_BODEGA"), any(), any(), any());
    }

    // ── ALMACEN_BASICO → rubro_operativo = OTRO (temporal hasta migración) ──────

    @Test
    @DisplayName("ALMACEN_BASICO deriva rubro=OTRO hasta que exista rubro_operativo propio")
    void almacenBasico_derivaRubroOtro() {
        PlanCatalogo plan = PlanCatalogo.builder()
                .codigo("ALMACEN_BASICO").segmento("ALMACENES").rubroOperativo("OTRO")
                .nivel("BASICO").precioMensual(new BigDecimal("89.00"))
                .maxUsuarios(5).maxSucursales(1).maxProductos(2000).activo(true).build();

        when(planCatalogoRepository.findByCodigoAndActivoTrue("ALMACEN_BASICO"))
                .thenReturn(Optional.of(plan));

        authService.registrar(buildRequest("ALMACEN_BASICO", "BASICO"));

        verify(tenantService).crearTenant(any(), eq("OTRO"), any(), any(), any());
    }

    // ── Sin planCodigo: usa el rubro enviado en el request ───────────────────────

    @Test
    @DisplayName("Sin planCodigo el rubro del request se pasa directamente a crearTenant")
    void sinPlanCodigo_usaRubroDelRequest() {
        authService.registrar(RegistrationRequestDTO.builder()
                .email("test@test.com").contraseña("password1").nombre("Test")
                .nombreFarmacia("Mi Negocio").planId("BASICO").rubro("VETERINARIA")
                .build());

        verify(tenantService).crearTenant(any(), eq("VETERINARIA"), any(), any(), any());
        verifyNoInteractions(planCatalogoRepository);
    }

    // ── Precio: nunca del cliente, siempre del catálogo ─────────────────────────

    @Test
    @DisplayName("El precio de la suscripción viene del catálogo, nunca del request")
    void veterinariaBasico_usaPrecioCatalogo109() {
        PlanCatalogo plan = PlanCatalogo.builder()
                .codigo("VETERINARIA_BASICO").segmento("VETERINARIA").rubroOperativo("VETERINARIA")
                .nivel("BASICO").precioMensual(new BigDecimal("109.00"))
                .maxUsuarios(10).maxSucursales(1).maxProductos(2000).activo(true).build();

        when(planCatalogoRepository.findByCodigoAndActivoTrue("VETERINARIA_BASICO"))
                .thenReturn(Optional.of(plan));

        authService.registrar(buildRequest("VETERINARIA_BASICO", "BASICO"));

        verify(suscripcionService).crearSuscripcion(argThat(s ->
                new BigDecimal("109.00").compareTo(s.getPrecioMensual()) == 0
                        && "VETERINARIA_BASICO".equals(s.getPlanCodigo())
        ));
    }

    // ── Helper ───────────────────────────────────────────────────────────────────

    private RegistrationRequestDTO buildRequest(String planCodigo, String planId) {
        return RegistrationRequestDTO.builder()
                .email("test@test.com")
                .contraseña("password1")
                .nombre("Test")
                .nombreFarmacia("Mi Negocio")
                .planId(planId)
                .planCodigo(planCodigo)
                .build();
    }
}
