-- Adds purchase/entrada fields to movimientos_inventario (used mainly for ENTRADA)
ALTER TABLE movimientos_inventario
    ADD COLUMN referencia VARCHAR(100),
  ADD COLUMN proveedor_id BIGINT REFERENCES proveedores(id),
  ADD COLUMN costo_unitario NUMERIC(10,2),
  ADD COLUMN lote VARCHAR(50),
  ADD COLUMN fecha_vencimiento DATE;

CREATE INDEX IF NOT EXISTS idx_movimientos_proveedor_id ON movimientos_inventario(proveedor_id);
CREATE INDEX IF NOT EXISTS idx_movimientos_fecha_vencimiento ON movimientos_inventario(fecha_vencimiento);
CREATE INDEX IF NOT EXISTS idx_movimientos_referencia ON movimientos_inventario(referencia);