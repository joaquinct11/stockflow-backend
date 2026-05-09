CREATE TABLE unidad_medida (
                               id BIGSERIAL PRIMARY KEY,
                               nombre VARCHAR(100) NOT NULL,
                               abreviatura VARCHAR(20),
                               es_default BOOLEAN DEFAULT FALSE,
                               tenant_id VARCHAR(100) NULL REFERENCES tenants(tenant_id) ON DELETE CASCADE,
                               created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                               UNIQUE (nombre, tenant_id)
);