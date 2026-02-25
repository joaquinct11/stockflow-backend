CREATE TABLE permisos (
                          id BIGSERIAL PRIMARY KEY,
                          nombre VARCHAR(100) NOT NULL UNIQUE,
                          descripcion VARCHAR(255),
                          rol_id BIGINT REFERENCES roles(id),
                          created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_permisos_nombre ON permisos(nombre);
CREATE INDEX idx_permisos_rol_id ON permisos(rol_id);