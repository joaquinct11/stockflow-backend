-- V35: Create notas_credito table and add NC fields to ventas

CREATE TABLE notas_credito (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(100) NOT NULL,
    codigo VARCHAR(20) NOT NULL,
    devolucion_id BIGINT NOT NULL REFERENCES devoluciones(id),
    monto_total DECIMAL(12,2) NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    fecha_emision TIMESTAMP NOT NULL DEFAULT NOW(),
    fecha_vencimiento TIMESTAMP NOT NULL,
    fecha_uso TIMESTAMP,
    venta_uso_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_nc_codigo UNIQUE (codigo)
);

CREATE INDEX idx_nc_tenant ON notas_credito(tenant_id);
CREATE INDEX idx_nc_codigo ON notas_credito(codigo);

-- Add NC fields to ventas
ALTER TABLE ventas ADD COLUMN IF NOT EXISTS nota_credito_id BIGINT REFERENCES notas_credito(id);
ALTER TABLE ventas ADD COLUMN IF NOT EXISTS descuento_nota_credito DECIMAL(12,2) DEFAULT 0;
