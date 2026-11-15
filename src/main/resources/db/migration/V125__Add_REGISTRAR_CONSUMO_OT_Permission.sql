-- Permiso para registrar consumo de materiales en órdenes de trabajo (Fase 2A)
INSERT INTO permisos (nombre, descripcion, created_at)
VALUES ('REGISTRAR_CONSUMO_OT',
        'Registrar consumo de materiales en orden de trabajo',
        NOW())
ON CONFLICT (nombre) DO NOTHING;
