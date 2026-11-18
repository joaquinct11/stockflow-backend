-- V144: Permisos de visibilidad de información financiera.
-- La asignación a roles se hace en RolePermissionDefaults (en código).
-- Solo registra los permisos en el catálogo — no hay join table en BD.

INSERT INTO permisos (nombre, descripcion, created_at)
VALUES
    ('VER_COSTO_PRODUCTO',   'Ver costo unitario de productos en listados, inventario y kardex', NOW()),
    ('VER_MARGEN_GANANCIA',  'Ver margen de ganancia calculado sobre precio de venta vs costo',  NOW()),
    ('VER_VALOR_INVENTARIO', 'Ver valorización total del inventario al costo',                   NOW())
ON CONFLICT (nombre) DO NOTHING;
