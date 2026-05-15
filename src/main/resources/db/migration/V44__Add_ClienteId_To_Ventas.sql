-- ============================================================
-- V44: Asociar cliente a ventas
-- ============================================================
-- Agrega cliente_id (nullable) a la tabla ventas para identificar
-- al comprador. NULL = consumidor final (sin registrar).

ALTER TABLE ventas ADD COLUMN IF NOT EXISTS cliente_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_ventas_cliente_id ON ventas (cliente_id) WHERE cliente_id IS NOT NULL;
