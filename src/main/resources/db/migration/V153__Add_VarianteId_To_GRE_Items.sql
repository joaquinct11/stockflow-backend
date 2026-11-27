-- F-23: Soporte de variantes en items de GRE
-- Permite identificar exactamente qué variante (talla/color) se traslada.
ALTER TABLE guia_remision_items
    ADD COLUMN IF NOT EXISTS variante_id BIGINT REFERENCES producto_variantes(id);
