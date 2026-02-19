CREATE TABLE proveedores (
                             id BIGSERIAL PRIMARY KEY,
                             nombre VARCHAR(255) NOT NULL,
                             ruc VARCHAR(20) UNIQUE,
                             contacto VARCHAR(255),
                             telefono VARCHAR(20),
                             email VARCHAR(255),
                             direccion TEXT,
                             activo BOOLEAN DEFAULT true,
                             tenant_id VARCHAR(100),
                             created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_proveedores_ruc ON proveedores(ruc);
CREATE INDEX idx_proveedores_nombre ON proveedores(nombre);
CREATE INDEX idx_proveedores_tenant_id ON proveedores(tenant_id);
CREATE INDEX idx_proveedores_activo ON proveedores(activo);