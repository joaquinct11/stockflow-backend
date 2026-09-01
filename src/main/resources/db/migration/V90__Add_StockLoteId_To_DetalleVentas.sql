ALTER TABLE detalles_venta
    ADD COLUMN IF NOT EXISTS stock_lote_id BIGINT NULL;
