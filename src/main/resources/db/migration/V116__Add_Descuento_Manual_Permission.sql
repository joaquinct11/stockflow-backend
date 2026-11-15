-- V116: Permiso DESCUENTO_MANUAL para edición manual de precio en el POS.
-- Usado en rubros con precio negociable (ej. TIENDA_ROPA).
-- No se asigna automáticamente a ningún rol; debe concederse individualmente
-- a cada usuario que lo requiera a través de AdminController.asignarPermisos.
INSERT INTO permisos (nombre, descripcion, created_at)
VALUES ('DESCUENTO_MANUAL',
        'Permite al vendedor editar el precio de venta manualmente en el POS',
        NOW())
ON CONFLICT (nombre) DO NOTHING;
