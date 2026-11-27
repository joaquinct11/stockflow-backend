-- F-23: Trazabilidad de variante en el Kardex
-- Permite filtrar y mostrar el saldo por variante específica.
ALTER TABLE movimientos_inventario
    ADD COLUMN IF NOT EXISTS variante_id BIGINT REFERENCES producto_variantes(id);
