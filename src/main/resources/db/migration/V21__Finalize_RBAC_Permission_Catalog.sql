-- V21: Finalize RBAC – canonical permission catalog
-- Ensures every permission code referenced by the application exists in the
-- permisos table.  All inserts are idempotent (ON CONFLICT DO NOTHING) so
-- this migration is safe to re-apply or run against an already-populated DB.
--
-- Permission-code conventions:
--   VER_*             – read / list
--   CREAR_*           – create
--   EDITAR_*          – update
--   ELIMINAR_*        – hard delete
--   CAMBIAR_ESTADO_*  – activate / deactivate
--
-- Base role defaults are now handled entirely in Java (RolePermissionDefaults).
-- The rol_id column is intentionally left NULL here so that the canonical rows
-- carry no stale role assignment and the Java layer is the single source of truth.

INSERT INTO permisos (nombre, descripcion, created_at) VALUES
    -- Dashboard
    ('VER_DASHBOARD',              'Permiso para ver el dashboard',                              NOW()),
    -- Proveedores
    ('VER_PROVEEDORES',            'Permiso para listar proveedores',                            NOW()),
    ('CREAR_PROVEEDOR',            'Permiso para crear proveedores',                             NOW()),
    ('EDITAR_PROVEEDOR',           'Permiso para editar proveedores',                            NOW()),
    ('ELIMINAR_PROVEEDOR',         'Permiso para eliminar proveedores',                          NOW()),
    ('CAMBIAR_ESTADO_PROVEEDOR',   'Permiso para activar/desactivar proveedores',                NOW()),
    -- Productos
    ('VER_PRODUCTOS',              'Permiso para listar productos',                              NOW()),
    ('CREAR_PRODUCTO',             'Permiso para crear productos',                               NOW()),
    ('EDITAR_PRODUCTO',            'Permiso para editar productos',                              NOW()),
    ('ELIMINAR_PRODUCTO',          'Permiso para eliminar productos',                            NOW()),
    -- Ventas
    ('VER_VENTAS',                 'Permiso para ver todas las ventas',                          NOW()),
    ('VER_MIS_VENTAS',             'Permiso para ver las propias ventas (vendedor)',              NOW()),
    ('CREAR_VENTA',                'Permiso para crear ventas',                                  NOW()),
    ('VER_DETALLE_VENTA',          'Permiso para ver el detalle de una venta',                   NOW()),
    ('ELIMINAR_VENTA',             'Permiso para eliminar ventas',                               NOW()),
    -- Inventario
    ('VER_INVENTARIO',             'Permiso para listar movimientos de inventario',              NOW()),
    ('CREAR_INVENTARIO',           'Permiso para registrar movimientos de inventario',           NOW()),
    ('VER_DETALLE_INVENTARIO',     'Permiso para ver el detalle de un movimiento de inventario', NOW()),
    ('ELIMINAR_INVENTARIO',        'Permiso para eliminar movimientos de inventario',            NOW()),
    -- Usuarios
    ('VER_USUARIOS',               'Permiso para listar usuarios',                               NOW()),
    ('CREAR_USUARIO',              'Permiso para crear usuarios',                                NOW()),
    ('EDITAR_USUARIO',             'Permiso para editar usuarios',                               NOW()),
    ('ELIMINAR_USUARIO',           'Permiso para eliminar usuarios',                             NOW()),
    ('CAMBIAR_ESTADO_USUARIO',     'Permiso para activar/desactivar usuarios',                   NOW()),
    -- Suscripciones
    ('VER_SUSCRIPCIONES',          'Permiso para listar suscripciones',                          NOW()),
    ('CREAR_SUSCRIPCION',          'Permiso para crear suscripciones',                           NOW()),
    ('EDITAR_SUSCRIPCION',         'Permiso para editar suscripciones',                          NOW()),
    ('ELIMINAR_SUSCRIPCION',       'Permiso para eliminar suscripciones',                        NOW()),
    ('CAMBIAR_ESTADO_SUSCRIPCION', 'Permiso para activar/cancelar suscripciones',                NOW()),
    -- Reportes
    ('VER_REPORTES',               'Permiso para ver reportes',                                  NOW()),
    -- Permisos (admin / super-admin)
    ('VER_PERMISOS',               'Permiso para consultar el catálogo de permisos',             NOW())
ON CONFLICT (nombre) DO NOTHING;

-- Clean up the legacy typo 'VER_DATELLE_INVENTARIO' if it was ever inserted,
-- keeping only the correctly-spelled 'VER_DETALLE_INVENTARIO'.
DELETE FROM permisos WHERE nombre = 'VER_DATELLE_INVENTARIO';

-- Remove the stale GESTIONAR_USUARIOS permission (replaced by granular
-- CREAR_USUARIO / EDITAR_USUARIO / ELIMINAR_USUARIO / VER_USUARIOS).
-- First move any usuario_permisos references to the new granular codes so no
-- user loses access, then delete the old row.
INSERT INTO usuario_permisos (usuario_id, permiso_id, tenant_id, created_at)
    SELECT up.usuario_id,
           (SELECT id FROM permisos WHERE nombre = 'CREAR_USUARIO'),
           up.tenant_id,
           NOW()
    FROM usuario_permisos up
    JOIN permisos p ON up.permiso_id = p.id
    WHERE p.nombre = 'GESTIONAR_USUARIOS'
      AND (SELECT id FROM permisos WHERE nombre = 'CREAR_USUARIO') IS NOT NULL
ON CONFLICT DO NOTHING;

INSERT INTO usuario_permisos (usuario_id, permiso_id, tenant_id, created_at)
    SELECT up.usuario_id,
           (SELECT id FROM permisos WHERE nombre = 'EDITAR_USUARIO'),
           up.tenant_id,
           NOW()
    FROM usuario_permisos up
    JOIN permisos p ON up.permiso_id = p.id
    WHERE p.nombre = 'GESTIONAR_USUARIOS'
      AND (SELECT id FROM permisos WHERE nombre = 'EDITAR_USUARIO') IS NOT NULL
ON CONFLICT DO NOTHING;

DELETE FROM usuario_permisos
WHERE permiso_id = (SELECT id FROM permisos WHERE nombre = 'GESTIONAR_USUARIOS');

DELETE FROM permisos WHERE nombre = 'GESTIONAR_USUARIOS';
