-- V38: Token de activación para nuevos usuarios creados por el admin
ALTER TABLE usuarios
    ADD COLUMN IF NOT EXISTS token_activacion       VARCHAR(255) UNIQUE,
    ADD COLUMN IF NOT EXISTS token_activacion_expira TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_usuarios_token_activacion
    ON usuarios (token_activacion)
    WHERE token_activacion IS NOT NULL;
