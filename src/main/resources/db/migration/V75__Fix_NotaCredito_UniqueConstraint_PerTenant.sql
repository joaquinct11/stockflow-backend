-- La constraint uk_nc_codigo era global (solo sobre 'codigo'), lo que impedía que
-- distintos tenants tuvieran su propia secuencia NC-YYYY-NNNNN.
-- Se reemplaza por una constraint compuesta (tenant_id, codigo).

ALTER TABLE notas_credito DROP CONSTRAINT IF EXISTS uk_nc_codigo;

ALTER TABLE notas_credito
    ADD CONSTRAINT uk_nc_codigo_tenant UNIQUE (tenant_id, codigo);
