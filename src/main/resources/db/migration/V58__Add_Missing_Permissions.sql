-- ============================================================
-- V58: Permisos faltantes identificados en auditoría RBAC
-- ============================================================
-- RETIRO_CAJA       — retiro parcial de caja (antes usaba ABRIR_CAJA)
-- VER_MIS_FACTURACION — ver solo los comprobantes de las propias ventas (default vendedor)
-- ENVIAR_OC         — enviar OC al proveedor (antes usaba EDITAR_OC)
-- CANCELAR_OC       — cancelar una OC (antes usaba EDITAR_OC)

INSERT INTO permisos (nombre, descripcion, created_at) VALUES
    ('RETIRO_CAJA',          'Permiso para registrar retiros parciales de efectivo en caja', NOW()),
    ('VER_MIS_FACTURACION',  'Permiso para ver únicamente los comprobantes de las propias ventas', NOW()),
    ('ENVIAR_OC',            'Permiso para enviar una orden de compra al proveedor', NOW()),
    ('CANCELAR_OC',          'Permiso para cancelar una orden de compra', NOW())
ON CONFLICT (nombre) DO NOTHING;
