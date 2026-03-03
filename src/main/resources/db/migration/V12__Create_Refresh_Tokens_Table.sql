CREATE TABLE refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    token VARCHAR(500) NOT NULL UNIQUE,
    usuario_id BIGINT NOT NULL,
    expiracion TIMESTAMP NOT NULL,
    creado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    revocado BOOLEAN DEFAULT FALSE,
    CONSTRAINT fk_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE
);

CREATE INDEX idx_refresh_token ON refresh_tokens(token);
CREATE INDEX idx_usuario_id ON refresh_tokens(usuario_id);
