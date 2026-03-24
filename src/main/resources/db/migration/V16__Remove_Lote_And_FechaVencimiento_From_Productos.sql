-- Remove lote and fecha_vencimiento from productos; these fields now live in movimientos_inventario (ENTRADA)
ALTER TABLE productos
    DROP COLUMN IF EXISTS lote,
    DROP COLUMN IF EXISTS fecha_vencimiento;
