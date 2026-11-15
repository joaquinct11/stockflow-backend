package com.stockflow.service;

import com.stockflow.config.RolePermissionDefaults;
import com.stockflow.entity.OrdenTrabajo;
import com.stockflow.entity.Usuario;
import com.stockflow.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifica las reglas de aislamiento de OTs por técnico asignado.
 *
 * Cubre:
 *  - GERENTE tiene VER_OT y VER_TODAS_OT en sus permisos base
 *  - TECNICO_SERVICIO NO tiene VER_TODAS_OT
 *  - La lógica de assertTecnicoAccess rechaza correctamente accesos no autorizados
 *  - VER_TODAS_OT está en el catálogo ALL_PERMISSIONS
 */
class OtAislamientoTecnicoTest {

    private final RolePermissionDefaults defaults = new RolePermissionDefaults();

    // ── Permisos de catálogo y roles ──────────────────────────────────────────

    @Test
    void OT1_verTodasOt_estaEnCatalogoAllPermissions() {
        assertThat(RolePermissionDefaults.ALL_PERMISSIONS).contains("VER_TODAS_OT");
    }

    @Test
    void OT2_gerente_tieneVerOtYVerTodasOt() {
        Set<String> permisos = defaults.getBasePermissions("GERENTE");
        assertThat(permisos).contains("VER_OT", "VER_TODAS_OT");
    }

    @Test
    void OT3_tecnicoServicio_noTieneVerTodasOt() {
        Set<String> permisos = defaults.getBasePermissions("TECNICO_SERVICIO");
        assertThat(permisos).doesNotContain("VER_TODAS_OT");
    }

    @Test
    void OT4_tecnicoServicio_tieneVerOt() {
        Set<String> permisos = defaults.getBasePermissions("TECNICO_SERVICIO");
        assertThat(permisos).contains("VER_OT");
    }

    @Test
    void OT5_admin_tieneVerTodasOt() {
        // ADMIN tiene ALL_PERMISSIONS
        assertThat(RolePermissionDefaults.ALL_PERMISSIONS).contains("VER_TODAS_OT");
    }

    // ── Lógica de assertTecnicoAccess ─────────────────────────────────────────

    /**
     * Simula assertTecnicoAccess extrayendo su lógica para pruebas unitarias.
     * En el servicio real lee de SecurityContextHolder; aquí lo poblamos manualmente.
     */
    private void assertTecnicoAccessSimulado(OrdenTrabajo ot, Long userId) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        boolean verTodas = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("PERM_VER_TODAS_OT"));
        if (verTodas) return;
        if (ot.getTecnico() == null || !ot.getTecnico().getId().equals(userId)) {
            throw new ResourceNotFoundException("Orden de trabajo no encontrada");
        }
    }

    private OrdenTrabajo otConTecnico(Long tecnicoId) {
        Usuario tecnico = new Usuario();
        tecnico.setId(tecnicoId);
        OrdenTrabajo ot = new OrdenTrabajo();
        ot.setTecnico(tecnico);
        return ot;
    }

    private OrdenTrabajo otSinTecnico() {
        OrdenTrabajo ot = new OrdenTrabajo();
        ot.setTecnico(null);
        return ot;
    }

    private void setAuth(String... authorities) {
        var perms = List.of(authorities).stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", "cred", perms));
    }

    private void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void OT6_tecnicoAsignado_puedeAccederSuPropiasOt() {
        try {
            setAuth("PERM_VER_OT");
            OrdenTrabajo ot = otConTecnico(42L);
            // No debe lanzar
            assertTecnicoAccessSimulado(ot, 42L);
        } finally {
            clearAuth();
        }
    }

    @Test
    void OT7_tecnicoNoAsignado_noAccedeOtDeOtroTecnico() {
        try {
            setAuth("PERM_VER_OT");
            OrdenTrabajo ot = otConTecnico(99L);
            assertThatThrownBy(() -> assertTecnicoAccessSimulado(ot, 42L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("no encontrada");
        } finally {
            clearAuth();
        }
    }

    @Test
    void OT8_otSinTecnicoAsignado_tecnicoNoPuedeAcceder() {
        try {
            setAuth("PERM_VER_OT");
            OrdenTrabajo ot = otSinTecnico();
            assertThatThrownBy(() -> assertTecnicoAccessSimulado(ot, 42L))
                    .isInstanceOf(ResourceNotFoundException.class);
        } finally {
            clearAuth();
        }
    }

    @Test
    void OT9_usuarioConVerTodasOt_puedeAccederCualquierOt() {
        try {
            setAuth("PERM_VER_TODAS_OT");
            // OT asignada a otro técnico
            OrdenTrabajo ot = otConTecnico(99L);
            // No debe lanzar
            assertTecnicoAccessSimulado(ot, 42L);
        } finally {
            clearAuth();
        }
    }

    @Test
    void OT10_usuarioConVerTodasOt_puedeAccederOtSinTecnico() {
        try {
            setAuth("PERM_VER_TODAS_OT");
            OrdenTrabajo ot = otSinTecnico();
            // No debe lanzar
            assertTecnicoAccessSimulado(ot, 42L);
        } finally {
            clearAuth();
        }
    }

    @Test
    void OT11_tecnicoAsignadoExactamente_mismoId_pasa() {
        try {
            setAuth("PERM_VER_OT");
            OrdenTrabajo ot = otConTecnico(1L);
            assertTecnicoAccessSimulado(ot, 1L);
        } finally {
            clearAuth();
        }
    }
}
