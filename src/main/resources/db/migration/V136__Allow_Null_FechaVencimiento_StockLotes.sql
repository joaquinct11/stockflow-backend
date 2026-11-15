-- V136: Permitir NULL en fecha_vencimiento para lotes de rubros sin vencimiento (ej. FERRETERÍA).
-- Los lotes farmacéuticos existentes conservan su fecha_vencimiento.
-- La lógica FEFO en StockLoteRepository ya trata NULL como "nunca vence" (NULLS LAST).
ALTER TABLE stock_lotes ALTER COLUMN fecha_vencimiento DROP NOT NULL;
