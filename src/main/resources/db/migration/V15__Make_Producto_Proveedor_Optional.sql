-- Make proveedor_id optional in productos to keep Producto as a clean catalog entity
ALTER TABLE productos
    ALTER COLUMN proveedor_id DROP NOT NULL;
