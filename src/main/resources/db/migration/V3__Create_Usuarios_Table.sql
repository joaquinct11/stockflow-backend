CREATE TABLE usuarios (
                          id BIGSERIAL PRIMARY KEY,
                          email VARCHAR(150) NOT NULL UNIQUE,
                          contraseña VARCHAR(255) NOT NULL,
                          nombre VARCHAR(150) NOT NULL,
                          rol_id BIGINT NOT NULL REFERENCES roles(id),
                          activo BOOLEAN DEFAULT true,
                          deleted_at TIMESTAMP DEFAULT NULL,
                          fecha_creacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                          ultimo_login TIMESTAMP,
                          tenant_id VARCHAR(100) NOT NULL REFERENCES tenants(tenant_id) ON DELETE CASCADE,
                          created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_usuarios_email ON usuarios(email);
CREATE INDEX idx_usuarios_tenant_id ON usuarios(tenant_id);
CREATE INDEX idx_usuarios_activo ON usuarios(activo);
CREATE INDEX idx_usuarios_deleted_at ON usuarios(deleted_at);
CREATE INDEX idx_usuarios_email_tenant ON usuarios(email, tenant_id);