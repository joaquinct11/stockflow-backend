CREATE TABLE tenants (
                         id BIGSERIAL PRIMARY KEY,
                         tenant_id VARCHAR(100) NOT NULL UNIQUE,
                         nombre VARCHAR(255) NOT NULL,
                         activo BOOLEAN DEFAULT true,
                         deleted_at TIMESTAMP DEFAULT NULL,
                         created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                         updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_tenants_tenant_id ON tenants(tenant_id);
CREATE INDEX idx_tenants_nombre ON tenants(nombre);
CREATE INDEX idx_tenants_activo ON tenants(activo);
CREATE INDEX idx_tenants_deleted_at ON tenants(deleted_at);