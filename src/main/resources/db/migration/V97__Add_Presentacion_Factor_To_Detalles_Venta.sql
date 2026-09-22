-- Soporte de multi-presentación en detalles de venta.
-- presentacion_id: referencia opcional a la presentación elegida en el POS
-- factor: cuántas unidades base = 1 de la presentación vendida (Ej: CAJA=20, BLISTER=10)

ALTER TABLE detalles_venta
    ADD COLUMN presentacion_id BIGINT      DEFAULT NULL,
    ADD COLUMN factor          INTEGER NOT NULL DEFAULT 1 CHECK (factor >= 1);

CREATE INDEX idx_detalles_venta_presentacion ON detalles_venta(presentacion_id);
