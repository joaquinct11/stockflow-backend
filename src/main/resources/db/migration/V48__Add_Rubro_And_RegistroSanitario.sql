-- Añade el tipo de negocio (rubro) a la tabla de tenants.
-- El valor por defecto 'OTRO' cubre los tenants existentes.
ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS rubro VARCHAR(50) NOT NULL DEFAULT 'OTRO';

-- Añade registro sanitario a recepcion_detalle.
-- Opcional por defecto; la lógica de negocio lo exige sólo para BOTICA/FARMACIA.
ALTER TABLE recepcion_detalle
    ADD COLUMN IF NOT EXISTS registro_sanitario VARCHAR(100);
