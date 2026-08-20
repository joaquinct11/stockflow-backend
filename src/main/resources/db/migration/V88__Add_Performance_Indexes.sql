-- Índices para eliminar full table scans en endpoints del dashboard
-- ventas: filtros más frecuentes son (tenant_id + created_at) y (tenant_id + vendedor_id)
CREATE INDEX IF NOT EXISTS idx_ventas_tenant_created
    ON ventas (tenant_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ventas_tenant_vendedor_created
    ON ventas (tenant_id, vendedor_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ventas_tenant_sucursal_created
    ON ventas (tenant_id, sucursal_id, created_at DESC);

-- movimientos_inventario: el endpoint ?dias=N filtra por (tenant_id + created_at)
CREATE INDEX IF NOT EXISTS idx_movimientos_tenant_created
    ON movimientos_inventario (tenant_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_movimientos_tenant_sucursal_created
    ON movimientos_inventario (tenant_id, sucursal_id, created_at DESC);
