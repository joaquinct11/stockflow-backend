-- Precios mayoristas por producto (opcional)
-- precioMayorista: precio unitario cuando la cantidad >= cantidadMinimaMayorista
-- cantidadMinimaMayorista: umbral entero positivo para activar el precio mayorista
-- Ambos campos son nullable. Si son NULL el producto funciona exactamente igual que antes.
ALTER TABLE productos
    ADD COLUMN precio_mayorista       NUMERIC(10, 2)  NULL,
    ADD COLUMN cantidad_minima_mayorista INTEGER       NULL;
