package com.stockflow.service;

import com.stockflow.config.RolePermissionDefaults;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica la matriz de permisos de Citas por rol.
 *
 * Matriz esperada:
 *   ADMIN, GERENTE      → todos los permisos de citas
 *   RECEPCIONISTA       → VER_CITAS, CREAR_CITA, EDITAR_CITA, CAMBIAR_ESTADO_CITA, CANCELAR_CITA
 *   VETERINARIO         → VER_CITAS, CAMBIAR_ESTADO_CITA
 *   AUXILIAR_VETERINARIO→ VER_CITAS
 *   GROOMER             → VER_CITAS
 *   VENDEDOR            → ninguno
 *   GESTOR_INVENTARIO   → ninguno
 */
@DisplayName("Citas — matriz de permisos por rol")
class CitaPermisosRolTest {

    private final RolePermissionDefaults rpd = new RolePermissionDefaults();

    private static final Set<String> TODOS_CITAS = Set.of(
            "VER_CITAS", "CREAR_CITA", "EDITAR_CITA", "CAMBIAR_ESTADO_CITA", "CANCELAR_CITA");

    // ── 1. ADMIN ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("1. ADMIN — todos los permisos")
    class Admin {
        @Test
        @DisplayName("ADMIN contiene todos los permisos de citas")
        void admin_tieneTodosLosCitas() {
            assertThat(rpd.getBasePermissions("ADMIN")).containsAll(TODOS_CITAS);
        }

        @Test
        @DisplayName("ALL_PERMISSIONS incluye los 5 permisos de citas")
        void allPermissions_incluyeCitas() {
            assertThat(RolePermissionDefaults.ALL_PERMISSIONS).containsAll(TODOS_CITAS);
        }
    }

    // ── 2. GERENTE ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("2. GERENTE — todos los permisos de citas")
    class Gerente {
        @Test
        @DisplayName("GERENTE tiene todos los permisos de citas")
        void gerente_tieneTodosCitas() {
            assertThat(rpd.getBasePermissions("GERENTE")).containsAll(TODOS_CITAS);
        }
    }

    // ── 3. RECEPCIONISTA ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("3. RECEPCIONISTA — gestión completa de agenda")
    class Recepcionista {
        @Test
        @DisplayName("RECEPCIONISTA tiene todos los permisos de citas")
        void recepcionista_tieneTodosCitas() {
            Set<String> perms = rpd.getBasePermissions("RECEPCIONISTA");
            assertThat(perms).containsAll(TODOS_CITAS);
        }
    }

    // ── 4. VETERINARIO ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("4. VETERINARIO — ver y cambiar estado")
    class Veterinario {
        @Test
        @DisplayName("VETERINARIO tiene VER_CITAS y CAMBIAR_ESTADO_CITA")
        void veterinario_tieneVerYCambiarEstado() {
            Set<String> perms = rpd.getBasePermissions("VETERINARIO");
            assertThat(perms).contains("VER_CITAS", "CAMBIAR_ESTADO_CITA");
        }

        @Test
        @DisplayName("VETERINARIO NO tiene CREAR_CITA ni EDITAR_CITA")
        void veterinario_noTieneCrearEditar() {
            Set<String> perms = rpd.getBasePermissions("VETERINARIO");
            assertThat(perms).doesNotContain("CREAR_CITA", "EDITAR_CITA", "CANCELAR_CITA");
        }
    }

    // ── 5. AUXILIAR_VETERINARIO ───────────────────────────────────────────────

    @Nested
    @DisplayName("5. AUXILIAR_VETERINARIO — solo lectura")
    class AuxiliarVeterinario {
        @Test
        @DisplayName("AUXILIAR_VETERINARIO tiene VER_CITAS")
        void auxiliar_tieneVerCitas() {
            assertThat(rpd.getBasePermissions("AUXILIAR_VETERINARIO")).contains("VER_CITAS");
        }

        @Test
        @DisplayName("AUXILIAR_VETERINARIO no puede crear ni cambiar estado de citas")
        void auxiliar_noTieneOtrosCitas() {
            Set<String> perms = rpd.getBasePermissions("AUXILIAR_VETERINARIO");
            assertThat(perms).doesNotContain("CREAR_CITA", "EDITAR_CITA", "CAMBIAR_ESTADO_CITA", "CANCELAR_CITA");
        }
    }

    // ── 6. GROOMER ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("6. GROOMER — solo lectura")
    class Groomer {
        @Test
        @DisplayName("GROOMER tiene VER_CITAS")
        void groomer_tieneVerCitas() {
            assertThat(rpd.getBasePermissions("GROOMER")).contains("VER_CITAS");
        }

        @Test
        @DisplayName("GROOMER no puede crear ni modificar citas")
        void groomer_noTieneOtrosCitas() {
            Set<String> perms = rpd.getBasePermissions("GROOMER");
            assertThat(perms).doesNotContain("CREAR_CITA", "EDITAR_CITA", "CAMBIAR_ESTADO_CITA", "CANCELAR_CITA");
        }
    }

    // ── 7. Roles comerciales no reciben permisos de citas ─────────────────────

    @Nested
    @DisplayName("7. Roles comerciales — sin permisos de citas")
    class RolesComerciales {
        @Test
        @DisplayName("VENDEDOR no tiene ningún permiso de citas")
        void vendedor_sinCitas() {
            Set<String> perms = rpd.getBasePermissions("VENDEDOR");
            assertThat(perms).doesNotContainAnyElementsOf(TODOS_CITAS);
        }

        @Test
        @DisplayName("GESTOR_INVENTARIO no tiene ningún permiso de citas")
        void gestorInventario_sinCitas() {
            Set<String> perms = rpd.getBasePermissions("GESTOR_INVENTARIO");
            assertThat(perms).doesNotContainAnyElementsOf(TODOS_CITAS);
        }
    }
}
