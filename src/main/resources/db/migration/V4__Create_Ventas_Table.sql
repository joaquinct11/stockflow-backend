CREATE TABLE ventas (
                        id BIGSERIAL PRIMARY KEY,
                        vendedor_id BIGINT NOT NULL REFERENCES usuarios(id),
                        total NUMERIC(10, 2) NOT NULL,
                        metodo_pago VARCHAR(50),
                        estado VARCHAR(50) DEFAULT 'COMPLETADA',
                        tenant_id VARCHAR(100),
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ventas_vendedor_id ON ventas(vendedor_id);
CREATE INDEX idx_ventas_tenant_id ON ventas(tenant_id);
CREATE INDEX idx_ventas_fecha ON ventas(created_at);
CREATE INDEX idx_ventas_estado ON ventas(estado);