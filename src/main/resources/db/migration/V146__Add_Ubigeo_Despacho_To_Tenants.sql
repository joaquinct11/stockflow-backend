-- V146: Punto de despacho predeterminado para GRE en tenants sin sucursales.
-- No reemplaza los datos fiscales (ruc, direccion, ciudad).
-- Se usa como tercer nivel de fallback en resolveUbigeo() solo para puntoDePartida.
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS ubigeo_despacho      VARCHAR(10);
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS direccion_despacho   VARCHAR(500);
