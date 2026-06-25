INSERT INTO permisos (nombre, descripcion, created_at) VALUES
    ('VER_CERTIFICADOS',     'Ver certificados del establecimiento', NOW()),
    ('CREAR_CERTIFICADO',    'Crear certificados', NOW()),
    ('EDITAR_CERTIFICADO',   'Editar certificados', NOW()),
    ('ELIMINAR_CERTIFICADO', 'Eliminar certificados', NOW())
ON CONFLICT (nombre) DO NOTHING;
