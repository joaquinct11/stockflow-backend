-- Elimina usuarios.sucursal_id: la fuente de verdad para sucursal por tenant
-- es usuario_tenant.sucursal_id (migrado en V101).
-- Todo el código productivo ya usa usuario_tenant; el campo en la entidad
-- Usuario fue eliminado. Esta migración finaliza la limpieza en la BD.

ALTER TABLE usuarios DROP COLUMN IF EXISTS sucursal_id;

-- Eliminar el índice compuesto que ya no tiene sentido (si existe)
DROP INDEX IF EXISTS idx_usuarios_sucursal_id;
