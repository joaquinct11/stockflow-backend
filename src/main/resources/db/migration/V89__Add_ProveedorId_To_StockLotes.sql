ALTER TABLE stock_lotes
    ADD COLUMN IF NOT EXISTS proveedor_id BIGINT NULL;

CREATE INDEX IF NOT EXISTS idx_stock_lotes_proveedor_id ON stock_lotes(proveedor_id);
