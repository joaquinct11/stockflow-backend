-- V103: Eliminar columnas legacy usuarios.rol_id y usuarios.tenant_id
-- Estas columnas fueron reemplazadas por usuario_tenant como fuente de verdad.

ALTER TABLE usuarios DROP CONSTRAINT IF EXISTS usuarios_rol_id_fkey;
ALTER TABLE usuarios DROP CONSTRAINT IF EXISTS usuarios_tenant_id_fkey;

DROP INDEX IF EXISTS idx_usuarios_email_tenant;
DROP INDEX IF EXISTS idx_usuarios_tenant_id;

ALTER TABLE usuarios DROP COLUMN IF EXISTS rol_id;
ALTER TABLE usuarios DROP COLUMN IF EXISTS tenant_id;
