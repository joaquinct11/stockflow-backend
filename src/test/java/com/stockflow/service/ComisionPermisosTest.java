package com.stockflow.service;

import com.stockflow.config.RolePermissionDefaults;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifica que los permisos de comisiones están presentes en el catálogo ALL_PERMISSIONS
 * y se comportan correctamente por rol.
 *
 * Sin contexto Spring — usa la clase directamente.
 */
class ComisionPermisosTest {

    private final RolePermissionDefaults defaults = new RolePermissionDefaults();

    // ── Catálogo (ALL_PERMISSIONS) ──────────────────────────────────────────

    @Test
    void catalogo_contiene_VER_COMISIONES() {
        assertTrue(RolePermissionDefaults.ALL_PERMISSIONS.contains("VER_COMISIONES"),
            "VER_COMISIONES debe estar en ALL_PERMISSIONS para que ADMIN lo reciba como permiso base");
    }

    @Test
    void catalogo_contiene_CREAR_COMISION() {
        assertTrue(RolePermissionDefaults.ALL_PERMISSIONS.contains("CREAR_COMISION"),
            "CREAR_COMISION debe estar en ALL_PERMISSIONS");
    }

    @Test
    void catalogo_contiene_EDITAR_COMISION() {
        assertTrue(RolePermissionDefaults.ALL_PERMISSIONS.contains("EDITAR_COMISION"),
            "EDITAR_COMISION debe estar en ALL_PERMISSIONS");
    }

    @Test
    void catalogo_contiene_ELIMINAR_COMISION() {
        assertTrue(RolePermissionDefaults.ALL_PERMISSIONS.contains("ELIMINAR_COMISION"),
            "ELIMINAR_COMISION debe estar en ALL_PERMISSIONS");
    }

    // ── ADMIN recibe todos los permisos del catálogo ────────────────────────

    @Test
    void admin_recibe_VER_COMISIONES_como_permiso_base() {
        assertTrue(defaults.getBasePermissions("ADMIN").contains("VER_COMISIONES"),
            "ADMIN debe tener VER_COMISIONES en sus permisos base (ALL_PERMISSIONS)");
    }

    @Test
    void admin_recibe_CREAR_COMISION_como_permiso_base() {
        assertTrue(defaults.getBasePermissions("ADMIN").contains("CREAR_COMISION"),
            "ADMIN debe tener CREAR_COMISION en sus permisos base");
    }

    // ── Principio de mínimo privilegio: otros roles NO reciben comisiones por defecto ──

    @Test
    void gerente_no_tiene_VER_COMISIONES_por_defecto() {
        assertFalse(defaults.getBasePermissions("GERENTE").contains("VER_COMISIONES"),
            "GERENTE no debe tener VER_COMISIONES como permiso base; se asigna individualmente");
    }

    @Test
    void vendedor_no_tiene_VER_COMISIONES_por_defecto() {
        assertFalse(defaults.getBasePermissions("VENDEDOR").contains("VER_COMISIONES"),
            "VENDEDOR no debe tener VER_COMISIONES como permiso base");
    }

    @Test
    void gestor_inventario_no_tiene_VER_COMISIONES_por_defecto() {
        assertFalse(defaults.getBasePermissions("GESTOR_INVENTARIO").contains("VER_COMISIONES"),
            "GESTOR_INVENTARIO no debe tener VER_COMISIONES como permiso base");
    }

    @Test
    void rol_desconocido_retorna_set_vacio() {
        assertTrue(defaults.getBasePermissions("ROL_INEXISTENTE").isEmpty(),
            "Un rol desconocido debe retornar un set vacío, nunca null");
    }
}
