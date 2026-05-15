-- ============================================================
-- V43: Permisos del módulo Gastos / Egresos
-- ============================================================

INSERT INTO permisos (nombre, descripcion, created_at) VALUES
    ('VER_GASTOS',     'Permiso para ver el registro de gastos y egresos',   NOW()),
    ('CREAR_GASTO',    'Permiso para registrar nuevos gastos',                NOW()),
    ('EDITAR_GASTO',   'Permiso para editar gastos existentes',               NOW()),
    ('ELIMINAR_GASTO', 'Permiso para eliminar gastos del registro',           NOW())
ON CONFLICT (nombre) DO NOTHING;
