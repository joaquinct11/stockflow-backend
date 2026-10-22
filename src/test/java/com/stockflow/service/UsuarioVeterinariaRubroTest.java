package com.stockflow.service;

import com.stockflow.config.RolePermissionDefaults;
import com.stockflow.entity.Suscripcion;
import com.stockflow.entity.Tenant;
import com.stockflow.exception.BadRequestException;
import com.stockflow.repository.ProductoRepository;
import com.stockflow.repository.SuscripcionRepository;
import com.stockflow.repository.TenantRepository;
import com.stockflow.repository.UsuarioRepository;
import com.stockflow.repository.UsuarioTenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Roles veterinarios — restricción por rubro")
class UsuarioVeterinariaRubroTest {

    private static final String TENANT_VET      = "vet-abc123";
    private static final String TENANT_BOTICA   = "botica-lima01";
    private static final String TENANT_FARMACIA = "farmacia-norte";
    private static final String TENANT_FERRET   = "ferreteria-sur";

    @Mock private SuscripcionRepository   suscripcionRepository;
    @Mock private UsuarioRepository       usuarioRepository;
    @Mock private ProductoRepository      productoRepository;
    @Mock private UsuarioTenantRepository usuarioTenantRepository;
    @Mock private TenantRepository        tenantRepository;

    @InjectMocks private PlanLimitService planLimitService;

    private Tenant tenantVet;
    private Tenant tenantBotica;
    private Tenant tenantFarmacia;
    private Tenant tenantFerret;

    @BeforeEach
    void setUp() {
        tenantVet      = Tenant.builder().tenantId(TENANT_VET).rubro("VETERINARIA").activo(true).build();
        tenantBotica   = Tenant.builder().tenantId(TENANT_BOTICA).rubro("BOTICA").activo(true).build();
        tenantFarmacia = Tenant.builder().tenantId(TENANT_FARMACIA).rubro("FARMACIA").activo(true).build();
        tenantFerret   = Tenant.builder().tenantId(TENANT_FERRET).rubro("FERRETERIA").activo(true).build();

        when(tenantRepository.findByTenantId(TENANT_VET)).thenReturn(Optional.of(tenantVet));
        when(tenantRepository.findByTenantId(TENANT_BOTICA)).thenReturn(Optional.of(tenantBotica));
        when(tenantRepository.findByTenantId(TENANT_FARMACIA)).thenReturn(Optional.of(tenantFarmacia));
        when(tenantRepository.findByTenantId(TENANT_FERRET)).thenReturn(Optional.of(tenantFerret));

        // Plan BASICO en todos los tenants; sin usuarios para no saturar el límite
        Suscripcion subBasico = Suscripcion.builder().planId("BASICO").tenantId(TENANT_VET).build();
        when(suscripcionRepository.findFirstByTenantIdOrderByIdDesc(TENANT_VET)).thenReturn(Optional.of(subBasico));
        when(suscripcionRepository.findFirstByTenantIdOrderByIdDesc(TENANT_BOTICA)).thenReturn(Optional.empty());
        when(suscripcionRepository.findFirstByTenantIdOrderByIdDesc(TENANT_FARMACIA)).thenReturn(Optional.empty());
        when(suscripcionRepository.findFirstByTenantIdOrderByIdDesc(TENANT_FERRET)).thenReturn(Optional.empty());

        when(usuarioTenantRepository.countByTenantIdAndActivoTrue(anyString())).thenReturn(0L);
        when(productoRepository.countByTenantId(anyString())).thenReturn(0L);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Test 1 — Cada rol veterinario se crea correctamente (validarRolPermitido no lanza)
    // ──────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("1. Roles veterinarios válidos en tenant VETERINARIA")
    class RolesVeterinariosEnVeterinaria {

        @Test
        @DisplayName("VETERINARIO puede asignarse en plan Básico con rubro VETERINARIA")
        void veterinario_enVeterinaria_ok() {
            assertThatCode(() -> planLimitService.validarRolPermitido(TENANT_VET, "VETERINARIO"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("RECEPCIONISTA puede asignarse en plan Básico con rubro VETERINARIA")
        void recepcionista_enVeterinaria_ok() {
            assertThatCode(() -> planLimitService.validarRolPermitido(TENANT_VET, "RECEPCIONISTA"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("AUXILIAR_VETERINARIO puede asignarse en plan Básico con rubro VETERINARIA")
        void auxiliar_enVeterinaria_ok() {
            assertThatCode(() -> planLimitService.validarRolPermitido(TENANT_VET, "AUXILIAR_VETERINARIO"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("GROOMER puede asignarse en plan Básico con rubro VETERINARIA")
        void groomer_enVeterinaria_ok() {
            assertThatCode(() -> planLimitService.validarRolPermitido(TENANT_VET, "GROOMER"))
                    .doesNotThrowAnyException();
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Test 3 — Roles veterinarios NO pueden asignarse a otros rubros
    // ──────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("3. Roles veterinarios rechazados en tenants no veterinarios")
    class RolesVetRechazadosEnOtrosRubros {

        @Test
        @DisplayName("VETERINARIO rechazado en BOTICA (plan Básico)")
        void veterinario_enBotica_lanzaException() {
            assertThatThrownBy(() -> planLimitService.validarRolPermitido(TENANT_BOTICA, "VETERINARIO"))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("RECEPCIONISTA rechazado en FARMACIA (plan Básico)")
        void recepcionista_enFarmacia_lanzaException() {
            assertThatThrownBy(() -> planLimitService.validarRolPermitido(TENANT_FARMACIA, "RECEPCIONISTA"))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("GROOMER rechazado en FERRETERIA (plan Básico)")
        void groomer_enFerreteria_lanzaException() {
            assertThatThrownBy(() -> planLimitService.validarRolPermitido(TENANT_FERRET, "GROOMER"))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("AUXILIAR_VETERINARIO rechazado en BOTICA (plan Básico)")
        void auxiliar_enBotica_lanzaException() {
            assertThatThrownBy(() -> planLimitService.validarRolPermitido(TENANT_BOTICA, "AUXILIAR_VETERINARIO"))
                    .isInstanceOf(BadRequestException.class);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Test 5 — ADMIN conserva todos los permisos
    // ──────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("5. ADMIN conserva todos los permisos del catálogo")
    class AdminConservaTodosLosPermisos {

        @Test
        @DisplayName("ADMIN tiene los 4 permisos veterinarios en ALL_PERMISSIONS")
        void admin_tienePermisosVeterinarios() {
            Set<String> all = RolePermissionDefaults.ALL_PERMISSIONS;
            assertThat(all).contains(
                    "VER_MASCOTAS", "CREAR_MASCOTA", "EDITAR_MASCOTA", "CAMBIAR_ESTADO_MASCOTA"
            );
        }

        @Test
        @DisplayName("getBasePermissions(ADMIN) devuelve ALL_PERMISSIONS")
        void getBasePermissions_admin_esAllPermissions() {
            RolePermissionDefaults rpd = new RolePermissionDefaults();
            assertThat(rpd.getBasePermissions("ADMIN")).isEqualTo(RolePermissionDefaults.ALL_PERMISSIONS);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Test 2 — Cada rol tiene los permisos base esperados
    // ──────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("2. Permisos base de cada rol veterinario")
    class PermisosBaseDeRolesVeterinarios {

        private final RolePermissionDefaults rpd = new RolePermissionDefaults();

        @Test
        @DisplayName("VETERINARIO tiene VER/CREAR/EDITAR_MASCOTA pero NO CAMBIAR_ESTADO_MASCOTA")
        void veterinario_tieneVerCrearEditar_noEstado() {
            Set<String> perms = rpd.getBasePermissions("VETERINARIO");
            assertThat(perms).contains("VER_MASCOTAS", "CREAR_MASCOTA", "EDITAR_MASCOTA");
            assertThat(perms).doesNotContain("CAMBIAR_ESTADO_MASCOTA");
        }

        @Test
        @DisplayName("RECEPCIONISTA tiene VER/CREAR/EDITAR_MASCOTA pero NO CAMBIAR_ESTADO_MASCOTA")
        void recepcionista_tieneVerCrearEditar_noEstado() {
            Set<String> perms = rpd.getBasePermissions("RECEPCIONISTA");
            assertThat(perms).contains("VER_MASCOTAS", "CREAR_MASCOTA", "EDITAR_MASCOTA");
            assertThat(perms).doesNotContain("CAMBIAR_ESTADO_MASCOTA");
        }

        @Test
        @DisplayName("AUXILIAR_VETERINARIO solo tiene VER_MASCOTAS, no crear/editar/cambiar estado")
        void auxiliar_soloVerMascotas() {
            Set<String> perms = rpd.getBasePermissions("AUXILIAR_VETERINARIO");
            assertThat(perms).contains("VER_MASCOTAS");
            assertThat(perms).doesNotContain("CREAR_MASCOTA", "EDITAR_MASCOTA", "CAMBIAR_ESTADO_MASCOTA");
        }

        @Test
        @DisplayName("GROOMER solo tiene VER_MASCOTAS, no crear/editar/cambiar estado")
        void groomer_soloVerMascotas() {
            Set<String> perms = rpd.getBasePermissions("GROOMER");
            assertThat(perms).contains("VER_MASCOTAS");
            assertThat(perms).doesNotContain("CREAR_MASCOTA", "EDITAR_MASCOTA", "CAMBIAR_ESTADO_MASCOTA");
        }

        @Test
        @DisplayName("GERENTE tiene los 4 permisos veterinarios como base")
        void gerente_tienePermisosVeterinarios() {
            Set<String> perms = rpd.getBasePermissions("GERENTE");
            assertThat(perms).contains(
                    "VER_MASCOTAS", "CREAR_MASCOTA", "EDITAR_MASCOTA", "CAMBIAR_ESTADO_MASCOTA"
            );
        }

        @Test
        @DisplayName("ADMIN tiene los 4 permisos veterinarios via ALL_PERMISSIONS")
        void admin_tieneLosCuatroPermisosVet() {
            Set<String> perms = rpd.getBasePermissions("ADMIN");
            assertThat(perms).contains(
                    "VER_MASCOTAS", "CREAR_MASCOTA", "EDITAR_MASCOTA", "CAMBIAR_ESTADO_MASCOTA"
            );
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Test 6 — VENDEDOR y GESTOR_INVENTARIO no reciben permisos veterinarios
    // ──────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("6. VENDEDOR y GESTOR_INVENTARIO sin permisos veterinarios")
    class RolesGeneralesSinPermisosVet {

        private final RolePermissionDefaults rpd = new RolePermissionDefaults();

        @Test
        @DisplayName("VENDEDOR no tiene ningún permiso veterinario")
        void vendedor_sinPermisosVet() {
            Set<String> perms = rpd.getBasePermissions("VENDEDOR");
            assertThat(perms).doesNotContain(
                    "VER_MASCOTAS", "CREAR_MASCOTA", "EDITAR_MASCOTA", "CAMBIAR_ESTADO_MASCOTA"
            );
        }

        @Test
        @DisplayName("GESTOR_INVENTARIO no tiene ningún permiso veterinario")
        void gestorInventario_sinPermisosVet() {
            Set<String> perms = rpd.getBasePermissions("GESTOR_INVENTARIO");
            assertThat(perms).doesNotContain(
                    "VER_MASCOTAS", "CREAR_MASCOTA", "EDITAR_MASCOTA", "CAMBIAR_ESTADO_MASCOTA"
            );
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Test 8 — Rol veterinario en veterinaria con plan Básico respeta límite de usuarios
    // ──────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("8. Plan Básico en veterinaria — rol vet + límite de usuarios")
    class PlanBasicoVeterinariaLimites {

        @Test
        @DisplayName("rol VETERINARIO permitido en plan Básico cuando rubro=VETERINARIA")
        void veterinario_basicoVeterinaria_permitido() {
            assertThatCode(() -> planLimitService.validarRolPermitido(TENANT_VET, "VETERINARIO"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("límite de usuarios en plan Básico veterinaria sigue siendo 5")
        void basicoVeterinaria_limiteUsuarios_5() {
            when(usuarioTenantRepository.countByTenantIdAndActivoTrue(TENANT_VET))
                    .thenReturn((long) PlanLimitService.BASICO_MAX_USUARIOS);

            assertThatThrownBy(() -> planLimitService.validarLimiteUsuarios(TENANT_VET))
                    .isInstanceOf(BadRequestException.class);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Test 10 — Roles actuales de Botica y Ferretería conservan permisos y restricciones
    // ──────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("10. Roles generales en otros rubros — sin cambios")
    class RolesGeneralesOtrosRubros {

        @Test
        @DisplayName("ADMIN puede asignarse en BOTICA (plan Básico)")
        void admin_enBotica_ok() {
            assertThatCode(() -> planLimitService.validarRolPermitido(TENANT_BOTICA, "ADMIN"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("VENDEDOR puede asignarse en FARMACIA (plan Básico)")
        void vendedor_enFarmacia_ok() {
            assertThatCode(() -> planLimitService.validarRolPermitido(TENANT_FARMACIA, "VENDEDOR"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("GESTOR_INVENTARIO puede asignarse en FERRETERIA (plan Básico)")
        void gestorInventario_enFerreteria_ok() {
            assertThatCode(() -> planLimitService.validarRolPermitido(TENANT_FERRET, "GESTOR_INVENTARIO"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("GERENTE sigue rechazado en plan Básico (cualquier rubro no veterinario)")
        void gerente_enBotica_planBasico_rechazado() {
            assertThatThrownBy(() -> planLimitService.validarRolPermitido(TENANT_BOTICA, "GERENTE"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("plan Básico");
        }

        @Test
        @DisplayName("GERENTE sigue rechazado en plan Básico en VETERINARIA también")
        void gerente_enVeterinaria_planBasico_rechazado() {
            assertThatThrownBy(() -> planLimitService.validarRolPermitido(TENANT_VET, "GERENTE"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("plan Básico");
        }
    }
}
