-- Agrega presentacion_id a guia_remision_items para recalcular cantidadBase
-- al validar stock y registrar movimientos de inventario en GRE.
ALTER TABLE guia_remision_items
    ADD COLUMN IF NOT EXISTS presentacion_id BIGINT;
