ALTER TABLE comprobantes ADD COLUMN IF NOT EXISTS sucursal_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_comprobantes_sucursal
    ON comprobantes (tenant_id, sucursal_id);
