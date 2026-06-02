-- ============================================================
-- V41: Permisos del módulo Clientes
-- ============================================================
-- Los códigos ya están en RolePermissionDefaults.ALL_PERMISSIONS
-- y en los @PreAuthorize de ClienteController.
-- Esta migración los inserta en la tabla permisos para que
-- aparezcan en el módulo de Gestión de Permisos (admin PRO).

INSERT INTO permisos (nombre, descripcion, created_at) VALUES
    ('VER_CLIENTES',           'Permiso para listar clientes',                 NOW()),
    ('CREAR_CLIENTE',          'Permiso para registrar clientes',              NOW()),
    ('EDITAR_CLIENTE',         'Permiso para editar datos de clientes',        NOW()),
    ('ELIMINAR_CLIENTE',       'Permiso para eliminar clientes',               NOW()),
    ('CAMBIAR_ESTADO_CLIENTE', 'Permiso para activar/desactivar clientes',     NOW())
ON CONFLICT (nombre) DO NOTHING;
