-- ============================================================
-- V50: Permiso ANULAR_VENTA
-- ============================================================
-- ANULAR_VENTA controla el endpoint PATCH /ventas/{id}/anular
-- que revierte stock y marca la venta como ANULADA.
-- Separado de ELIMINAR_VENTA para mayor granularidad.

INSERT INTO permisos (nombre, descripcion, created_at) VALUES
    ('ANULAR_VENTA', 'Permiso para anular ventas registradas (revierte el stock)', NOW())
ON CONFLICT (nombre) DO NOTHING;
