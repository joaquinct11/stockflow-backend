package com.stockflow.service;

import com.stockflow.config.RolePermissionDefaults;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reglas de permisos relevantes para el Dashboard:
 *
 *  - Bajo stock requiere VER_INVENTARIO (no solo VER_PRODUCTOS).
 *  - Stats de mascotas/clientes solo para tenant VETERINARIA con VER_MASCOTAS / VER_CLIENTES.
 *  - Roles comerciales (VENDEDOR, GESTOR_INVENTARIO) no reciben permisos veterinarios.
 *  - Roles vet no reciben VER_INVENTARIO.
 *  - ADMIN y GERENTE conservan todos los permisos autorizados.
 */
@DisplayName("Dashboard — reglas de permisos por rol")
class DashboardPermissionRulesTest {

    private final RolePermissionDefaults rpd = new RolePermissionDefaults();

    // ──────────────────────────────────────────────────────────────────────────
    // 1. Tarjeta Bajo Stock requiere VER_INVENTARIO
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("1. Bajo stock requiere VER_INVENTARIO")
    class BajoStockRequiereVerInventario {

        @Test
        @DisplayName("VENDEDOR tiene VER_PRODUCTOS pero NO VER_INVENTARIO → bajo stock oculto")
        void vendedor_noTieneVerInventario() {
            Set<String> perms = rpd.getBasePermissions("VENDEDOR");
            assertThat(perms).contains("VER_PRODUCTOS");
            assertThat(perms).doesNotContain("VER_INVENTARIO");
        }

        @Test
        @DisplayName("GESTOR_INVENTARIO tiene ambos VER_PRODUCTOS y VER_INVENTARIO → bajo stock visible")
        void gestorInventario_tieneAmbos() {
            Set<String> perms = rpd.getBasePermissions("GESTOR_INVENTARIO");
            assertThat(perms).contains("VER_PRODUCTOS", "VER_INVENTARIO");
        }

        @Test
        @DisplayName("GERENTE tiene VER_INVENTARIO → bajo stock visible")
        void gerente_tieneVerInventario() {
            Set<String> perms = rpd.getBasePermissions("GERENTE");
            assertThat(perms).contains("VER_INVENTARIO");
        }

        @Test
        @DisplayName("ADMIN tiene VER_INVENTARIO → bajo stock visible")
        void admin_tieneVerInventario() {
            Set<String> perms = rpd.getBasePermissions("ADMIN");
            assertThat(perms).contains("VER_INVENTARIO");
        }

        @Test
        @DisplayName("VETERINARIO tiene VER_PRODUCTOS pero NO VER_INVENTARIO → bajo stock oculto")
        void veterinario_noTieneVerInventario() {
            Set<String> perms = rpd.getBasePermissions("VETERINARIO");
            assertThat(perms).contains("VER_PRODUCTOS");
            assertThat(perms).doesNotContain("VER_INVENTARIO");
        }

        @Test
        @DisplayName("AUXILIAR_VETERINARIO tiene VER_PRODUCTOS pero NO VER_INVENTARIO → bajo stock oculto")
        void auxiliarVeterinario_noTieneVerInventario() {
            Set<String> perms = rpd.getBasePermissions("AUXILIAR_VETERINARIO");
            assertThat(perms).contains("VER_PRODUCTOS");
            assertThat(perms).doesNotContain("VER_INVENTARIO");
        }

        @Test
        @DisplayName("RECEPCIONISTA NO tiene VER_INVENTARIO ni VER_PRODUCTOS → bajo stock oculto")
        void recepcionista_noTieneInventarioNiProductos() {
            Set<String> perms = rpd.getBasePermissions("RECEPCIONISTA");
            assertThat(perms).doesNotContain("VER_INVENTARIO", "VER_PRODUCTOS");
        }

        @Test
        @DisplayName("GROOMER NO tiene VER_INVENTARIO ni VER_PRODUCTOS → bajo stock oculto")
        void groomer_noTieneInventarioNiProductos() {
            Set<String> perms = rpd.getBasePermissions("GROOMER");
            assertThat(perms).doesNotContain("VER_INVENTARIO", "VER_PRODUCTOS");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 2. Stats de mascotas: solo roles con VER_MASCOTAS
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("2. GET /mascotas/stats requiere VER_MASCOTAS")
    class MascotaStatsRequierePermiso {

        @Test
        @DisplayName("VENDEDOR NO tiene VER_MASCOTAS → no solicita stats de mascotas")
        void vendedor_noTieneVerMascotas() {
            assertThat(rpd.getBasePermissions("VENDEDOR")).doesNotContain("VER_MASCOTAS");
        }

        @Test
        @DisplayName("GESTOR_INVENTARIO NO tiene VER_MASCOTAS → no solicita stats de mascotas")
        void gestorInventario_noTieneVerMascotas() {
            assertThat(rpd.getBasePermissions("GESTOR_INVENTARIO")).doesNotContain("VER_MASCOTAS");
        }

        @Test
        @DisplayName("VETERINARIO SÍ tiene VER_MASCOTAS → puede solicitar stats de mascotas")
        void veterinario_tieneVerMascotas() {
            assertThat(rpd.getBasePermissions("VETERINARIO")).contains("VER_MASCOTAS");
        }

        @Test
        @DisplayName("RECEPCIONISTA SÍ tiene VER_MASCOTAS → puede solicitar stats de mascotas")
        void recepcionista_tieneVerMascotas() {
            assertThat(rpd.getBasePermissions("RECEPCIONISTA")).contains("VER_MASCOTAS");
        }

        @Test
        @DisplayName("AUXILIAR_VETERINARIO SÍ tiene VER_MASCOTAS")
        void auxiliarVeterinario_tieneVerMascotas() {
            assertThat(rpd.getBasePermissions("AUXILIAR_VETERINARIO")).contains("VER_MASCOTAS");
        }

        @Test
        @DisplayName("GROOMER SÍ tiene VER_MASCOTAS")
        void groomer_tieneVerMascotas() {
            assertThat(rpd.getBasePermissions("GROOMER")).contains("VER_MASCOTAS");
        }

        @Test
        @DisplayName("GERENTE SÍ tiene VER_MASCOTAS (gestión clínica)")
        void gerente_tieneVerMascotas() {
            assertThat(rpd.getBasePermissions("GERENTE")).contains("VER_MASCOTAS");
        }

        @Test
        @DisplayName("ADMIN SÍ tiene VER_MASCOTAS via ALL_PERMISSIONS")
        void admin_tieneVerMascotas() {
            assertThat(rpd.getBasePermissions("ADMIN")).contains("VER_MASCOTAS");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 3. Stats de clientes: solo roles con VER_CLIENTES
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("3. GET /clientes/stats requiere VER_CLIENTES")
    class ClienteStatsRequierePermiso {

        @Test
        @DisplayName("GESTOR_INVENTARIO NO tiene VER_CLIENTES → no solicita stats de clientes")
        void gestorInventario_noTieneVerClientes() {
            assertThat(rpd.getBasePermissions("GESTOR_INVENTARIO")).doesNotContain("VER_CLIENTES");
        }

        @Test
        @DisplayName("VENDEDOR SÍ tiene VER_CLIENTES (necesita ver clientes en POS)")
        void vendedor_tieneVerClientes() {
            assertThat(rpd.getBasePermissions("VENDEDOR")).contains("VER_CLIENTES");
        }

        @Test
        @DisplayName("VETERINARIO SÍ tiene VER_CLIENTES → puede solicitar stats de clientes")
        void veterinario_tieneVerClientes() {
            assertThat(rpd.getBasePermissions("VETERINARIO")).contains("VER_CLIENTES");
        }

        @Test
        @DisplayName("RECEPCIONISTA SÍ tiene VER_CLIENTES")
        void recepcionista_tieneVerClientes() {
            assertThat(rpd.getBasePermissions("RECEPCIONISTA")).contains("VER_CLIENTES");
        }

        @Test
        @DisplayName("GROOMER SÍ tiene VER_CLIENTES")
        void groomer_tieneVerClientes() {
            assertThat(rpd.getBasePermissions("GROOMER")).contains("VER_CLIENTES");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 4. Roles vet no tienen VER_INVENTARIO (stats de inventario ocultas)
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("4. Roles vet no tienen VER_INVENTARIO")
    class RolesVetSinVerInventario {

        @Test
        @DisplayName("VETERINARIO no tiene VER_INVENTARIO")
        void veterinario() {
            assertThat(rpd.getBasePermissions("VETERINARIO")).doesNotContain("VER_INVENTARIO");
        }

        @Test
        @DisplayName("RECEPCIONISTA no tiene VER_INVENTARIO")
        void recepcionista() {
            assertThat(rpd.getBasePermissions("RECEPCIONISTA")).doesNotContain("VER_INVENTARIO");
        }

        @Test
        @DisplayName("AUXILIAR_VETERINARIO no tiene VER_INVENTARIO")
        void auxiliarVeterinario() {
            assertThat(rpd.getBasePermissions("AUXILIAR_VETERINARIO")).doesNotContain("VER_INVENTARIO");
        }

        @Test
        @DisplayName("GROOMER no tiene VER_INVENTARIO")
        void groomer() {
            assertThat(rpd.getBasePermissions("GROOMER")).doesNotContain("VER_INVENTARIO");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 5. ADMIN y GERENTE conservan sus permisos autorizados
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("5. ADMIN y GERENTE conservan permisos autorizados")
    class AdminGerenteConservanPermisos {

        @Test
        @DisplayName("ADMIN tiene todos los permisos del catálogo (ALL_PERMISSIONS)")
        void admin_tieneTodosLosPermisos() {
            assertThat(rpd.getBasePermissions("ADMIN")).isEqualTo(RolePermissionDefaults.ALL_PERMISSIONS);
        }

        @Test
        @DisplayName("GERENTE tiene VER_PRODUCTOS, VER_INVENTARIO, VER_MASCOTAS, VER_CLIENTES, VER_VENTAS")
        void gerente_tienePermisosOperativos() {
            Set<String> perms = rpd.getBasePermissions("GERENTE");
            assertThat(perms).contains(
                    "VER_PRODUCTOS",
                    "VER_INVENTARIO",
                    "VER_MASCOTAS",
                    "VER_CLIENTES",
                    "VER_VENTAS"
            );
        }

        @Test
        @DisplayName("GERENTE no puede gestionar suscripciones ni eliminar usuarios")
        void gerente_noTienePermisosCriticos() {
            Set<String> perms = rpd.getBasePermissions("GERENTE");
            assertThat(perms).doesNotContain(
                    "CREAR_SUSCRIPCION",
                    "ELIMINAR_SUSCRIPCION",
                    "ELIMINAR_USUARIO",
                    "VER_PERMISOS"
            );
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 6. Rubros comerciales — ningún rol comercial tiene permisos veterinarios base
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("6. Roles comerciales sin permisos veterinarios")
    class RolesComerciales {

        private static final Set<String> PERMS_VET =
                Set.of("VER_MASCOTAS", "CREAR_MASCOTA", "EDITAR_MASCOTA", "CAMBIAR_ESTADO_MASCOTA");

        @Test
        @DisplayName("VENDEDOR no tiene ningún permiso veterinario")
        void vendedor() {
            assertThat(rpd.getBasePermissions("VENDEDOR")).doesNotContainAnyElementsOf(PERMS_VET);
        }

        @Test
        @DisplayName("GESTOR_INVENTARIO no tiene ningún permiso veterinario")
        void gestorInventario() {
            assertThat(rpd.getBasePermissions("GESTOR_INVENTARIO")).doesNotContainAnyElementsOf(PERMS_VET);
        }
    }
}
