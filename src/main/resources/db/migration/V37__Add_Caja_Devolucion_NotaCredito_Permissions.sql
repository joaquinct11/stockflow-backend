-- ============================================================
-- V37 — Nuevos permisos: Caja, Devoluciones y Notas de Crédito
-- ============================================================
-- Estos permisos ya están en RolePermissionDefaults.ALL_PERMISSIONS
-- y en los @PreAuthorize de los controladores correspondientes.
-- Esta migración los inserta en la tabla permisos para que aparezcan
-- en el módulo de Gestión de Permisos.

INSERT INTO permisos (nombre, descripcion, created_at) VALUES
    ('VER_CAJA',             'Permiso para ver la caja y su historial',              NOW()),
    ('ABRIR_CAJA',           'Permiso para abrir una nueva caja',                    NOW()),
    ('CERRAR_CAJA',          'Permiso para cerrar la caja activa',                   NOW()),
    ('VER_DEVOLUCIONES',     'Permiso para ver devoluciones de ventas',              NOW()),
    ('CREAR_DEVOLUCION',     'Permiso para registrar devoluciones de ventas',        NOW()),
    ('VER_NOTAS_CREDITO',    'Permiso para ver notas de crédito',                    NOW()),
    ('EMITIR_NOTA_CREDITO',  'Permiso para emitir y generar PDF de notas de crédito', NOW())
ON CONFLICT (nombre) DO NOTHING;
