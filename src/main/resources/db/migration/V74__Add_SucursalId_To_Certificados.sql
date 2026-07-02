-- Agrega sucursal_id a certificados_establecimiento
ALTER TABLE certificados_establecimiento
    ADD COLUMN IF NOT EXISTS sucursal_id BIGINT REFERENCES sucursales(id);

CREATE INDEX IF NOT EXISTS idx_certificados_sucursal
    ON certificados_establecimiento (sucursal_id);

-- Backfill: asignar la sucursal principal existente a todos los registros sin sucursal
UPDATE certificados_establecimiento ce
SET sucursal_id = s.id
FROM sucursales s
WHERE s.tenant_id = ce.tenant_id
  AND s.es_principal = true
  AND ce.sucursal_id IS NULL;
