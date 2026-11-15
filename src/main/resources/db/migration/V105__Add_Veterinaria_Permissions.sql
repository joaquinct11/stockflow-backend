-- ============================================================
-- V105: Permisos del módulo Veterinaria (mascotas)
-- ============================================================
INSERT INTO permisos (nombre, descripcion, created_at) VALUES
    ('VER_MASCOTAS',           'Listar y ver mascotas',                  NOW()),
    ('CREAR_MASCOTA',          'Registrar nueva mascota',                NOW()),
    ('EDITAR_MASCOTA',         'Editar datos de mascota',                NOW()),
    ('CAMBIAR_ESTADO_MASCOTA', 'Activar o desactivar mascota',           NOW())
ON CONFLICT (nombre) DO NOTHING;
