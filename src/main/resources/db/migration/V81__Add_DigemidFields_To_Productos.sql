ALTER TABLE productos
    ADD COLUMN IF NOT EXISTS registro_sanitario VARCHAR(100),
    ADD COLUMN IF NOT EXISTS cod_digemid        VARCHAR(20);
