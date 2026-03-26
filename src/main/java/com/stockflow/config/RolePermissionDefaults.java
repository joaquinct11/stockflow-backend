package com.stockflow.config;

import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Canonical permission catalog and role-default mappings for StockFlow RBAC.
 *
 * This is the single source of truth for:
 *  - The full permission code catalog.
 *  - The base (default) permissions that every user with a given role receives
 *    automatically, BEFORE any user-specific extras are applied.
 *
 * Both {@link JwtAuthenticationFilter} (SecurityContext authorities) and
 * {@link com.stockflow.service.impl.AuthServiceImpl} (/api/auth/me response)
 * must use this component so the two sources stay in sync.
 */
@Component
public class RolePermissionDefaults {

    /** Full canonical permission catalog. */
    public static final Set<String> ALL_PERMISSIONS = Set.of(
            // Dashboard
            "VER_DASHBOARD",
            // Proveedores
            "VER_PROVEEDORES", "CREAR_PROVEEDOR", "EDITAR_PROVEEDOR", "ELIMINAR_PROVEEDOR", "CAMBIAR_ESTADO_PROVEEDOR",
            // Productos
            "VER_PRODUCTOS", "CREAR_PRODUCTO", "EDITAR_PRODUCTO", "ELIMINAR_PRODUCTO",
            // Ventas
            "VER_VENTAS", "VER_MIS_VENTAS", "CREAR_VENTA", "VER_DETALLE_VENTA", "ELIMINAR_VENTA",
            // Inventario
            "VER_INVENTARIO", "CREAR_INVENTARIO", "VER_DETALLE_INVENTARIO", "ELIMINAR_INVENTARIO",
            // Usuarios
            "VER_USUARIOS", "CREAR_USUARIO", "EDITAR_USUARIO", "ELIMINAR_USUARIO", "CAMBIAR_ESTADO_USUARIO",
            // Suscripciones
            "VER_SUSCRIPCIONES", "CREAR_SUSCRIPCION", "EDITAR_SUSCRIPCION", "ELIMINAR_SUSCRIPCION", "CAMBIAR_ESTADO_SUSCRIPCION",
            // Reportes
            "VER_REPORTES",
            // Gestión de permisos (admin only)
            "VER_PERMISOS"
    );

    /**
     * Returns the set of base permission codes for the given role.
     * These permissions are granted automatically to all users with that role,
     * regardless of their individual {@code usuario_permisos} rows.
     *
     * <ul>
     *   <li><b>ADMIN</b> – all permissions (full override)</li>
     *   <li><b>GERENTE</b> – read-only access across all modules</li>
     *   <li><b>VENDEDOR</b> – dashboard, products list, own sales</li>
     *   <li><b>GESTOR_INVENTARIO</b> – dashboard, products CRUD, suppliers CRUD+state, inventory CRUD</li>
     * </ul>
     *
     * @param role the role name (e.g. "ADMIN", "GERENTE", "VENDEDOR", "GESTOR_INVENTARIO")
     * @return immutable set of permission code strings; never null
     */
    public Set<String> getBasePermissions(String role) {
        return switch (role) {
            case "ADMIN" -> ALL_PERMISSIONS;
            case "GERENTE" -> Set.of(
                    "VER_DASHBOARD",
                    "VER_VENTAS", "VER_DETALLE_VENTA",
                    "VER_PRODUCTOS",
                    "VER_PROVEEDORES",
                    "VER_INVENTARIO", "VER_DETALLE_INVENTARIO",
                    "VER_USUARIOS",
                    "VER_SUSCRIPCIONES",
                    "VER_REPORTES"
            );
            case "VENDEDOR" -> Set.of(
                    "VER_DASHBOARD",
                    "VER_PRODUCTOS",
                    "CREAR_VENTA", "VER_MIS_VENTAS", "VER_DETALLE_VENTA"
            );
            case "GESTOR_INVENTARIO" -> Set.of(
                    "VER_DASHBOARD",
                    "VER_PRODUCTOS", "CREAR_PRODUCTO", "EDITAR_PRODUCTO",
                    "VER_PROVEEDORES", "CREAR_PROVEEDOR", "EDITAR_PROVEEDOR", "CAMBIAR_ESTADO_PROVEEDOR",
                    "VER_INVENTARIO", "CREAR_INVENTARIO", "VER_DETALLE_INVENTARIO"
            );
            default -> Set.of();
        };
    }
}
