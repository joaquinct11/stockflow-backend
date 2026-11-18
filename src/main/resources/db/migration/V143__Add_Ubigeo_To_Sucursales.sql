-- V143: Ubigeo y dirección completa en sucursales.
-- Lucode requiere el ubigeo (6 dígitos SUNAT) en punto_de_partida y punto_de_llegada de las GRE.
-- direccion_completa es la dirección formateada para el documento tributario.
ALTER TABLE sucursales ADD COLUMN IF NOT EXISTS ubigeo              VARCHAR(10);
ALTER TABLE sucursales ADD COLUMN IF NOT EXISTS direccion_completa  VARCHAR(500);
