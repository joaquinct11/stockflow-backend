-- Add CAMBIAR_ESTADO_ permissions for state-change operations.
-- These follow the confirmed permission code convention and are intentionally
-- NOT assigned as base role permissions, so that only ADMIN (via role check)
-- or users explicitly granted these permissions can change entity state.
INSERT INTO permisos (nombre, descripcion, created_at) VALUES
    ('CAMBIAR_ESTADO_USUARIO',   'Permiso para activar/desactivar usuarios',   NOW()),
    ('CAMBIAR_ESTADO_PROVEEDOR', 'Permiso para activar/desactivar proveedores', NOW())
ON CONFLICT (nombre) DO NOTHING;
