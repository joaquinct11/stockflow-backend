-- V133: Permiso para anular la facturación de una OT (Fase 2G)
-- No se asigna a roles base; se concede individualmente según necesidad.

INSERT INTO permisos (nombre, descripcion, created_at)
VALUES ('ANULAR_FACTURA_OT', 'Anular la facturación de una Orden de Trabajo', NOW())
ON CONFLICT (nombre) DO NOTHING;
