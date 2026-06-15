-- Añade registro sanitario a movimientos_inventario para que la vista Lotes lo muestre.
-- Se copia desde recepcion_detalle al confirmar una recepción.
ALTER TABLE movimientos_inventario
    ADD COLUMN IF NOT EXISTS registro_sanitario VARCHAR(100);
