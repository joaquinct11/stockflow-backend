-- Agrega sucursal_id a usuario_tenant para que cada relación usuario↔tenant
-- tenga su propia sucursal asignada, en lugar de depender de usuarios.sucursal_id
-- que es una columna global incompatible con el modelo multi-tenant.
--
-- usuarios.sucursal_id se mantiene sin cambios (backward compat) hasta que
-- todas las referencias en código sean migradas a usuario_tenant.sucursal_id.

ALTER TABLE usuario_tenant
    ADD COLUMN IF NOT EXISTS sucursal_id BIGINT
        REFERENCES sucursales(id) ON DELETE SET NULL;

-- Migrar sucursal_id existente de usuarios hacia la fila correspondiente
-- en usuario_tenant. Solo aplica a filas donde la relación ya existe.
UPDATE usuario_tenant ut
SET    sucursal_id = u.sucursal_id
FROM   usuarios u
WHERE  ut.usuario_id = u.id
  AND  u.sucursal_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_ut_sucursal ON usuario_tenant(sucursal_id)
    WHERE sucursal_id IS NOT NULL;
