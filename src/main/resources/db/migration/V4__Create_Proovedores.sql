CREATE TABLE proveedores (
                             id BIGSERIAL PRIMARY KEY,
                             nombre VARCHAR(255) NOT NULL,
                             ruc VARCHAR(20) UNIQUE,
                             contacto VARCHAR(255),
                             telefono VARCHAR(20),
                             email VARCHAR(255),
                             direccion TEXT,
                             activo BOOLEAN DEFAULT true,
                             deleted_at TIMESTAMP DEFAULT NULL,
                             tenant_id VARCHAR(100) NOT NULL REFERENCES tenants(tenant_id) ON DELETE CASCADE,
                             created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_proveedores_ruc ON proveedores(ruc);
CREATE INDEX idx_proveedores_nombre ON proveedores(nombre);
CREATE INDEX idx_proveedores_tenant_id ON proveedores(tenant_id);
CREATE INDEX idx_proveedores_activo ON proveedores(activo);
CREATE INDEX idx_proveedores_deleted_at ON proveedores(deleted_at);