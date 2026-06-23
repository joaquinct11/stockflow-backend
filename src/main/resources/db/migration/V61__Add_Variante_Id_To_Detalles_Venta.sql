ALTER TABLE detalles_venta
    ADD COLUMN IF NOT EXISTS variante_id BIGINT REFERENCES producto_variantes(id);
