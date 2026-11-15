-- Fase 2D: permiso para facturar órdenes de trabajo cerradas técnicamente
INSERT INTO permisos (nombre, descripcion, created_at)
VALUES ('FACTURAR_OT',
        'Facturar una orden de trabajo en estado CERRADA_TECN, generando la venta asociada sin descontar inventario',
        NOW())
ON CONFLICT (nombre) DO NOTHING;
