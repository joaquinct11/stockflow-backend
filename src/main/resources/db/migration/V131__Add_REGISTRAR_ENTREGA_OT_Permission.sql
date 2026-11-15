-- Fase 2E: permiso para registrar la entrega del equipo al cliente
INSERT INTO permisos (nombre, descripcion, created_at)
VALUES ('REGISTRAR_ENTREGA_OT',
        'Registrar la entrega formal del equipo al cliente, cambiando el estado de FACTURADA a ENTREGADA',
        NOW())
ON CONFLICT (nombre) DO NOTHING;
