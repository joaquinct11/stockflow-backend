-- V117: Precio mayorista por presentación (Farmacia / Botica).
-- Agrega configuración mayorista opcional a cada presentación comercial.
-- Cuando precio_mayorista y cantidad_minima_mayorista son null,
-- el comportamiento existente (precio minorista fijo) se mantiene sin cambios.
ALTER TABLE producto_presentaciones
    ADD COLUMN precio_mayorista           NUMERIC(10, 2)  NULL,
    ADD COLUMN cantidad_minima_mayorista  INTEGER         NULL;
