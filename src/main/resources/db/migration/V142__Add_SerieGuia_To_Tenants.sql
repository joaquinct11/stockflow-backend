-- V142: Serie para guías de remisión por tenant.
-- Formato T001 (T + 3 dígitos), análogo a serieBoleta / serieFactura.
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS serie_guia VARCHAR(10) DEFAULT 'T001';
