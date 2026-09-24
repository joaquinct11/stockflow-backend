-- Factor de presentación en devoluciones: al devolver 1 blíster con factor=10,
-- se reponen 10 unidades base en el stock (coherente con el descuento en la venta).
ALTER TABLE devolucion_detalle
    ADD COLUMN factor INTEGER NOT NULL DEFAULT 1;
