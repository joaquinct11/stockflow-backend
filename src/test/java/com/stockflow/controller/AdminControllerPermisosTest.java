package com.stockflow.controller;

import com.stockflow.config.RolePermissionDefaults;
import com.stockflow.dto.UsuarioDTO;
import com.stockflow.entity.Rol;
import com.stockflow.entity.Usuario;
import com.stockflow.entity.UsuarioTenant;
import com.stockflow.mapper.UsuarioMapper;
import com.stockflow.repository.UsuarioTenantRepository;
import com.stockflow.service.PermisoService;
import com.stockflow.service.UsuarioPermisoService;
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
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Verifica que GET /admin/usuarios devuelve rolNombre y sucursalId desde UsuarioTenant,
 * con aislamiento por tenant activo.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AdminController — /admin/usuarios aislado por UsuarioTenant")
class AdminControllerPermisosTest {

    private static final String TENANT_VET    = "vet-ca35";
    private static final String TENANT_JUAN   = "juan-sac-95e4";
    private static final Long   USR_28        = 28L;
    private static final Long   USR_99        = 99L;
    private static final Long   SUCURSAL_19   = 19L;
    private static final String EMAIL_A       = "maria@gmail.com";
    private static final String EMAIL_B       = "victor@gmail.com";

    @Mock private PermisoService            permisoService;
    @Mock private UsuarioPermisoService     usuarioPermisoService;
    @Mock private UsuarioMapper             usuarioMapper;
    @Mock private UsuarioTenantRepository   usuarioTenantRepository;
    @Mock private RolePermissionDefaults    rolePermissionDefaults;

    @InjectMocks private AdminController adminController;

    private Rol rolVendedor;
    private Rol rolAdmin;
    private Rol rolGestor;
    private Usuario usuarioA;
    private Usuario usuarioB;
    private UsuarioTenant utVetVendedor;
    private UsuarioTenant utVetAdmin;
    private UsuarioTenant utJuanGestor;
    private UsuarioTenant utVetVendedorB;

    @BeforeEach
    void setUp() {
        rolVendedor = Rol.builder().id(2L).nombre("VENDEDOR").build();
        rolAdmin    = Rol.builder().id(1L).nombre("ADMIN").build();
        rolGestor   = Rol.builder().id(3L).nombre("GESTOR_INVENTARIO").build();

        usuarioA = Usuario.builder().id(USR_28).email(EMAIL_A).nombre("Maria").activo(true).build();
        usuarioB = Usuario.builder().id(USR_99).email(EMAIL_B).nombre("Victor").activo(true).build();

        utVetVendedor  = UsuarioTenant.builder().id(100L).usuario(usuarioA).tenantId(TENANT_VET)
                .rol(rolVendedor).sucursalId(SUCURSAL_19).activo(true).build();
        utVetAdmin     = UsuarioTenant.builder().id(101L).usuario(usuarioA).tenantId(TENANT_VET)
                .rol(rolAdmin).sucursalId(null).activo(true).build();
        utJuanGestor   = UsuarioTenant.builder().id(102L).usuario(usuarioA).tenantId(TENANT_JUAN)
                .rol(rolGestor).sucursalId(null).activo(true).build();
        utVetVendedorB = UsuarioTenant.builder().id(103L).usuario(usuarioB).tenantId(TENANT_VET)
                .rol(rolVendedor).sucursalId(null).activo(true).build();

        when(usuarioMapper.toDTO(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            return UsuarioDTO.builder().id(u.getId()).email(u.getEmail()).nombre(u.getNombre()).activo(u.getActivo()).build();
        });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── A: rolNombre viene de UsuarioTenant ───────────────────────────────────

    @Test
    @DisplayName("A — rolNombre en respuesta viene de UsuarioTenant, no de Usuario")
    void listarUsuarios_rolNombreDesdeUsuarioTenant() {
        TenantContext.setCurrentTenant(TENANT_VET);
        when(usuarioTenantRepository.findByTenantIdAndActivoTrue(TENANT_VET))
                .thenReturn(List.of(utVetVendedor));

        List<UsuarioDTO> result = adminController.listarUsuarios().getBody();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRolNombre()).isEqualTo("VENDEDOR");
    }

    // ── B: sucursalId viene de UsuarioTenant ──────────────────────────────────

    @Test
    @DisplayName("B — sucursalId en respuesta viene de UsuarioTenant")
    void listarUsuarios_sucursalIdDesdeUsuarioTenant() {
        TenantContext.setCurrentTenant(TENANT_VET);
        when(usuarioTenantRepository.findByTenantIdAndActivoTrue(TENANT_VET))
                .thenReturn(List.of(utVetVendedor));

        List<UsuarioDTO> result = adminController.listarUsuarios().getBody();

        assertThat(result.get(0).getSucursalId()).isEqualTo(19L);
    }

    // ── C: mismo usuario con diferente rol en dos tenants ────────────────────

    @Test
    @DisplayName("C — mismo usuario devuelve VENDEDOR en Vet y GESTOR_INVENTARIO en Juan SAC")
    void mismoUsuario_diferenteRolPorTenant() {
        // Vet
        TenantContext.setCurrentTenant(TENANT_VET);
        when(usuarioTenantRepository.findByTenantIdAndActivoTrue(TENANT_VET))
                .thenReturn(List.of(utVetVendedor));
        List<UsuarioDTO> enVet = adminController.listarUsuarios().getBody();

        // Juan SAC
        TenantContext.setCurrentTenant(TENANT_JUAN);
        when(usuarioTenantRepository.findByTenantIdAndActivoTrue(TENANT_JUAN))
                .thenReturn(List.of(utJuanGestor));
        List<UsuarioDTO> enJuan = adminController.listarUsuarios().getBody();

        assertThat(enVet.get(0).getRolNombre()).isEqualTo("VENDEDOR");
        assertThat(enJuan.get(0).getRolNombre()).isEqualTo("GESTOR_INVENTARIO");
    }

    // ── D: mismo usuario con diferente sucursal en dos tenants ───────────────

    @Test
    @DisplayName("D — mismo usuario devuelve sucursal 19 en Vet y null en Juan SAC")
    void mismoUsuario_diferenteSucursalPorTenant() {
        TenantContext.setCurrentTenant(TENANT_VET);
        when(usuarioTenantRepository.findByTenantIdAndActivoTrue(TENANT_VET))
                .thenReturn(List.of(utVetVendedor));
        List<UsuarioDTO> enVet = adminController.listarUsuarios().getBody();

        TenantContext.setCurrentTenant(TENANT_JUAN);
        when(usuarioTenantRepository.findByTenantIdAndActivoTrue(TENANT_JUAN))
                .thenReturn(List.of(utJuanGestor));
        List<UsuarioDTO> enJuan = adminController.listarUsuarios().getBody();

        assertThat(enVet.get(0).getSucursalId()).isEqualTo(19L);
        assertThat(enJuan.get(0).getSucursalId()).isNull();
    }

    // ── E: ADMIN devuelve rolNombre = "ADMIN", no null ───────────────────────

    @Test
    @DisplayName("E — usuario con rol ADMIN devuelve rolNombre='ADMIN', no null")
    void usuarioAdmin_devuelveRolNombreAdmin() {
        TenantContext.setCurrentTenant(TENANT_VET);
        when(usuarioTenantRepository.findByTenantIdAndActivoTrue(TENANT_VET))
                .thenReturn(List.of(utVetAdmin));

        List<UsuarioDTO> result = adminController.listarUsuarios().getBody();

        assertThat(result.get(0).getRolNombre()).isEqualTo("ADMIN");
        assertThat(result.get(0).getRolNombre()).isNotNull();
    }

    // ── F: getDefaultPermisos recibe el rol correcto ──────────────────────────

    @Test
    @DisplayName("F — rolNombre llega correctamente al llamar getDefaultPermisos")
    void listarUsuarios_rolNombreCorrectoParaDefaultPermisos() {
        TenantContext.setCurrentTenant(TENANT_VET);
        when(usuarioTenantRepository.findByTenantIdAndActivoTrue(TENANT_VET))
                .thenReturn(List.of(utVetVendedor));
        when(rolePermissionDefaults.getBasePermissions("VENDEDOR"))
                .thenReturn(java.util.Set.of("CREAR_VENTA", "VER_VENTAS"));

        List<UsuarioDTO> result = adminController.listarUsuarios().getBody();
        String rolObtenido = result.get(0).getRolNombre();

        // Simula lo que hace el frontend: obtiene permisos por defecto con este rolNombre
        ResponseEntity<List<String>> defaults = adminController.obtenerPermisosDefaultRol(rolObtenido);

        assertThat(rolObtenido).isEqualTo("VENDEDOR");
        assertThat(defaults.getStatusCode().value()).isEqualTo(200);
    }

    // ── G: usuario de otro tenant no aparece ─────────────────────────────────

    @Test
    @DisplayName("G — usuario de otro tenant no aparece en la lista del tenant actual")
    void usuarioOtroTenant_noApareceEnListaVet() {
        TenantContext.setCurrentTenant(TENANT_VET);
        // findByTenantIdAndActivoTrue solo devuelve UT de Vet
        when(usuarioTenantRepository.findByTenantIdAndActivoTrue(TENANT_VET))
                .thenReturn(List.of(utVetVendedor));
        // utJuanGestor (Juan SAC) nunca debe aparecer

        List<UsuarioDTO> result = adminController.listarUsuarios().getBody();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEmail()).isEqualTo(EMAIL_A);
        assertThat(result.stream().map(UsuarioDTO::getEmail)).doesNotContain(EMAIL_B);
    }

    // ── H: usuario inactivo en UT del tenant no aparece (activo=false filtra) ─

    @Test
    @DisplayName("H — findByTenantIdAndActivoTrue excluye UsuarioTenant con activo=false")
    void usuarioInactivoEnUT_noApareceEnLista() {
        // El repositorio ya aplica el filtro activoTrue — si UT está inactivo,
        // findByTenantIdAndActivoTrue no lo devuelve
        TenantContext.setCurrentTenant(TENANT_VET);
        when(usuarioTenantRepository.findByTenantIdAndActivoTrue(TENANT_VET))
                .thenReturn(List.of()); // vacío porque todos los UT están inactivos

        List<UsuarioDTO> result = adminController.listarUsuarios().getBody();

        assertThat(result).isEmpty();
    }

    // ── I: varios usuarios del mismo tenant devuelven sus propios roles ───────

    @Test
    @DisplayName("I — dos usuarios del mismo tenant devuelven sus propios rolNombre")
    void dosUsuariosMismoTenant_cadaUnoDevuelveSuRol() {
        TenantContext.setCurrentTenant(TENANT_VET);
        when(usuarioTenantRepository.findByTenantIdAndActivoTrue(TENANT_VET))
                .thenReturn(List.of(utVetAdmin, utVetVendedorB));

        List<UsuarioDTO> result = adminController.listarUsuarios().getBody();

        assertThat(result).hasSize(2);
        assertThat(result.stream().map(UsuarioDTO::getRolNombre))
                .containsExactlyInAnyOrder("ADMIN", "VENDEDOR");
    }

    // ── J: sucursalId null se mantiene null ───────────────────────────────────

    @Test
    @DisplayName("J — sucursalId null en UsuarioTenant se mantiene null en DTO")
    void sucursalIdNullEnUT_permanaceNullEnDTO() {
        TenantContext.setCurrentTenant(TENANT_VET);
        when(usuarioTenantRepository.findByTenantIdAndActivoTrue(TENANT_VET))
                .thenReturn(List.of(utVetAdmin)); // sucursalId = null

        List<UsuarioDTO> result = adminController.listarUsuarios().getBody();

        assertThat(result.get(0).getSucursalId()).isNull();
    }

    // ── K: tenantId viene de TenantContext, no nulo ───────────────────────────

    @Test
    @DisplayName("K — tenantId en DTO viene de TenantContext (no null)")
    void listarUsuarios_tenantIdNoNulo() {
        TenantContext.setCurrentTenant(TENANT_VET);
        when(usuarioTenantRepository.findByTenantIdAndActivoTrue(TENANT_VET))
                .thenReturn(List.of(utVetVendedor));

        List<UsuarioDTO> result = adminController.listarUsuarios().getBody();

        assertThat(result.get(0).getTenantId()).isNotNull();
        assertThat(result.get(0).getTenantId()).isEqualTo(TENANT_VET);
    }

    // ── L: tenantId es correcto por tenant (aislamiento) ─────────────────────

    @Test
    @DisplayName("L — tenantId correcto por tenant activo (aislamiento entre tenants)")
    void listarUsuarios_tenantIdAisladoPorTenant() {
        TenantContext.setCurrentTenant(TENANT_VET);
        when(usuarioTenantRepository.findByTenantIdAndActivoTrue(TENANT_VET))
                .thenReturn(List.of(utVetVendedor));
        List<UsuarioDTO> enVet = adminController.listarUsuarios().getBody();

        TenantContext.setCurrentTenant(TENANT_JUAN);
        when(usuarioTenantRepository.findByTenantIdAndActivoTrue(TENANT_JUAN))
                .thenReturn(List.of(utJuanGestor));
        List<UsuarioDTO> enJuan = adminController.listarUsuarios().getBody();

        assertThat(enVet.get(0).getTenantId()).isEqualTo(TENANT_VET);
        assertThat(enJuan.get(0).getTenantId()).isEqualTo(TENANT_JUAN);
    }
}
