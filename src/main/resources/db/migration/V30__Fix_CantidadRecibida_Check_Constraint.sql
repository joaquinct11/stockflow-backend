-- Permite cantidad_recibida = 0 para el pre-llenado desde OC (el usuario la actualiza después)
ALTER TABLE recepcion_detalle DROP CONSTRAINT IF EXISTS recepcion_detalle_cantidad_recibida_check;
