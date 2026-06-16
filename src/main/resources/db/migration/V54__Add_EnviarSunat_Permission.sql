INSERT INTO permisos (nombre, descripcion, created_at)
VALUES ('ENVIAR_SUNAT', 'Permiso para enviar comprobantes a SUNAT via OSE', NOW())
ON CONFLICT (nombre) DO NOTHING;
