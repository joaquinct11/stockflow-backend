-- ============================================================
-- V20: Standardize permissions catalog
-- Adds all missing permission codes needed to fully gate every
-- module action.  Each code is inserted with a canonical
-- rol_id so the JWT filter can load role-default authorities
-- via permisoRepository.findNombresByRolNombre().
-- Multi-role defaults are handled in AuthServiceImpl.permisosBasePorRol().
-- ============================================================

-- ── MÓDULO: PROVEEDORES ─────────────────────────────────────
-- VER_PROVEEDORES → GESTOR_INVENTARIO (ADMIN/GERENTE use role checks)
INSERT INTO permisos (nombre, descripcion, rol_id, created_at)
VALUES ('VER_PROVEEDORES', 'Ver lista de proveedores',
        (SELECT id FROM roles WHERE nombre = 'GESTOR_INVENTARIO'), NOW())
ON CONFLICT (nombre) DO NOTHING;

-- CREAR_PROVEEDOR → GESTOR_INVENTARIO
INSERT INTO permisos (nombre, descripcion, rol_id, created_at)
VALUES ('CREAR_PROVEEDOR', 'Crear proveedores',
        (SELECT id FROM roles WHERE nombre = 'GESTOR_INVENTARIO'), NOW())
ON CONFLICT (nombre) DO NOTHING;

-- EDITAR_PROVEEDOR → GESTOR_INVENTARIO
INSERT INTO permisos (nombre, descripcion, rol_id, created_at)
VALUES ('EDITAR_PROVEEDOR', 'Editar proveedores',
        (SELECT id FROM roles WHERE nombre = 'GESTOR_INVENTARIO'), NOW())
ON CONFLICT (nombre) DO NOTHING;

-- ACTIVAR_PROVEEDOR → GESTOR_INVENTARIO (can activate/deactivate their managed suppliers)
INSERT INTO permisos (nombre, descripcion, rol_id, created_at)
VALUES ('ACTIVAR_PROVEEDOR', 'Activar o desactivar proveedores',
        (SELECT id FROM roles WHERE nombre = 'GESTOR_INVENTARIO'), NOW())
ON CONFLICT (nombre) DO NOTHING;

-- ELIMINAR_PROVEEDOR → ADMIN
INSERT INTO permisos (nombre, descripcion, rol_id, created_at)
VALUES ('ELIMINAR_PROVEEDOR', 'Eliminar proveedores',
        (SELECT id FROM roles WHERE nombre = 'ADMIN'), NOW())
ON CONFLICT (nombre) DO NOTHING;

-- ── MÓDULO: PRODUCTOS ────────────────────────────────────────
-- VER_PRODUCTOS → GESTOR_INVENTARIO (ADMIN/GERENTE/VENDEDOR use role checks)
INSERT INTO permisos (nombre, descripcion, rol_id, created_at)
VALUES ('VER_PRODUCTOS', 'Ver lista de productos',
        (SELECT id FROM roles WHERE nombre = 'GESTOR_INVENTARIO'), NOW())
ON CONFLICT (nombre) DO NOTHING;

-- ── MÓDULO: VENTAS ───────────────────────────────────────────
-- VER_DETALLE_VENTA → VENDEDOR (can view details of their own sales)
INSERT INTO permisos (nombre, descripcion, rol_id, created_at)
VALUES ('VER_DETALLE_VENTA', 'Ver detalle de una venta',
        (SELECT id FROM roles WHERE nombre = 'VENDEDOR'), NOW())
ON CONFLICT (nombre) DO NOTHING;

-- ELIMINAR_VENTA → ADMIN
INSERT INTO permisos (nombre, descripcion, rol_id, created_at)
VALUES ('ELIMINAR_VENTA', 'Eliminar ventas',
        (SELECT id FROM roles WHERE nombre = 'ADMIN'), NOW())
ON CONFLICT (nombre) DO NOTHING;

-- ── MÓDULO: INVENTARIO ───────────────────────────────────────
-- VER_DETALLE_INVENTARIO → GESTOR_INVENTARIO
INSERT INTO permisos (nombre, descripcion, rol_id, created_at)
VALUES ('VER_DETALLE_INVENTARIO', 'Ver detalle de un movimiento de inventario',
        (SELECT id FROM roles WHERE nombre = 'GESTOR_INVENTARIO'), NOW())
ON CONFLICT (nombre) DO NOTHING;

-- EDITAR_INVENTARIO → GESTOR_INVENTARIO
INSERT INTO permisos (nombre, descripcion, rol_id, created_at)
VALUES ('EDITAR_INVENTARIO', 'Editar movimientos de inventario',
        (SELECT id FROM roles WHERE nombre = 'GESTOR_INVENTARIO'), NOW())
ON CONFLICT (nombre) DO NOTHING;

-- ELIMINAR_INVENTARIO → ADMIN
INSERT INTO permisos (nombre, descripcion, rol_id, created_at)
VALUES ('ELIMINAR_INVENTARIO', 'Eliminar movimientos de inventario',
        (SELECT id FROM roles WHERE nombre = 'ADMIN'), NOW())
ON CONFLICT (nombre) DO NOTHING;

-- ── MÓDULO: USUARIOS ─────────────────────────────────────────
INSERT INTO permisos (nombre, descripcion, rol_id, created_at)
VALUES ('VER_USUARIOS', 'Ver lista de usuarios',
        (SELECT id FROM roles WHERE nombre = 'ADMIN'), NOW())
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO permisos (nombre, descripcion, rol_id, created_at)
VALUES ('CREAR_USUARIO', 'Crear usuarios',
        (SELECT id FROM roles WHERE nombre = 'ADMIN'), NOW())
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO permisos (nombre, descripcion, rol_id, created_at)
VALUES ('EDITAR_USUARIO', 'Editar usuarios',
        (SELECT id FROM roles WHERE nombre = 'ADMIN'), NOW())
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO permisos (nombre, descripcion, rol_id, created_at)
VALUES ('ACTIVAR_USUARIO', 'Activar o desactivar usuarios',
        (SELECT id FROM roles WHERE nombre = 'ADMIN'), NOW())
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO permisos (nombre, descripcion, rol_id, created_at)
VALUES ('ELIMINAR_USUARIO', 'Eliminar usuarios',
        (SELECT id FROM roles WHERE nombre = 'ADMIN'), NOW())
ON CONFLICT (nombre) DO NOTHING;

-- ── MÓDULO: SUSCRIPCIONES ────────────────────────────────────
INSERT INTO permisos (nombre, descripcion, rol_id, created_at)
VALUES ('VER_SUSCRIPCIONES', 'Ver suscripciones',
        (SELECT id FROM roles WHERE nombre = 'ADMIN'), NOW())
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO permisos (nombre, descripcion, rol_id, created_at)
VALUES ('CREAR_SUSCRIPCION', 'Crear suscripciones',
        (SELECT id FROM roles WHERE nombre = 'ADMIN'), NOW())
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO permisos (nombre, descripcion, rol_id, created_at)
VALUES ('EDITAR_SUSCRIPCION', 'Editar suscripciones',
        (SELECT id FROM roles WHERE nombre = 'ADMIN'), NOW())
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO permisos (nombre, descripcion, rol_id, created_at)
VALUES ('ACTIVAR_SUSCRIPCION', 'Activar o desactivar suscripciones',
        (SELECT id FROM roles WHERE nombre = 'ADMIN'), NOW())
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO permisos (nombre, descripcion, rol_id, created_at)
VALUES ('ELIMINAR_SUSCRIPCION', 'Eliminar suscripciones',
        (SELECT id FROM roles WHERE nombre = 'ADMIN'), NOW())
ON CONFLICT (nombre) DO NOTHING;

-- ── MÓDULO: DASHBOARD ────────────────────────────────────────
-- VER_DASHBOARD → ADMIN (general/full view)
INSERT INTO permisos (nombre, descripcion, rol_id, created_at)
VALUES ('VER_DASHBOARD', 'Ver el dashboard general con datos de todos los usuarios',
        (SELECT id FROM roles WHERE nombre = 'ADMIN'), NOW())
ON CONFLICT (nombre) DO NOTHING;

-- VER_DASHBOARD_PROPIO → VENDEDOR (own-data view)
INSERT INTO permisos (nombre, descripcion, rol_id, created_at)
VALUES ('VER_DASHBOARD_PROPIO', 'Ver el dashboard con los propios datos del usuario',
        (SELECT id FROM roles WHERE nombre = 'VENDEDOR'), NOW())
ON CONFLICT (nombre) DO NOTHING;

-- ── MÓDULO: GESTIÓN DE PERMISOS (solo ADMIN) ─────────────────
INSERT INTO permisos (nombre, descripcion, rol_id, created_at)
VALUES ('VER_PERMISOS', 'Ver el catálogo de permisos disponibles',
        (SELECT id FROM roles WHERE nombre = 'ADMIN'), NOW())
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO permisos (nombre, descripcion, rol_id, created_at)
VALUES ('GESTIONAR_PERMISOS', 'Asignar y revocar permisos a usuarios',
        (SELECT id FROM roles WHERE nombre = 'ADMIN'), NOW())
ON CONFLICT (nombre) DO NOTHING;
