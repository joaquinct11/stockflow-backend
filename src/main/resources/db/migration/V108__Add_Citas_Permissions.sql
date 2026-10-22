-- ============================================================
-- V108: Permisos del módulo Citas Veterinaria
-- ============================================================
INSERT INTO permisos (nombre, descripcion, created_at) VALUES
    ('VER_CITAS',            'Listar y ver citas de la agenda',          NOW()),
    ('CREAR_CITA',           'Registrar nueva cita en la agenda',        NOW()),
    ('EDITAR_CITA',          'Editar / reprogramar una cita',            NOW()),
    ('CAMBIAR_ESTADO_CITA',  'Cambiar el estado de una cita',            NOW()),
    ('CANCELAR_CITA',        'Cancelar una cita',                        NOW())
ON CONFLICT (nombre) DO NOTHING;
