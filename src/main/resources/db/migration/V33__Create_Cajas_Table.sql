CREATE TABLE cajas (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(100) NOT NULL,
    usuario_id BIGINT NOT NULL REFERENCES usuarios(id),
    usuario_nombre VARCHAR(200) NOT NULL,
    monto_apertura DECIMAL(12,2) NOT NULL DEFAULT 0,
    -- Totales calculados al cerrar (desde ventas con esta caja_id)
    total_efectivo DECIMAL(12,2),
    total_tarjeta DECIMAL(12,2),
    total_yape_plin DECIMAL(12,2),
    total_ingresos DECIMAL(12,2),
    cantidad_ventas INTEGER DEFAULT 0,
    -- Cierre físico
    monto_contado DECIMAL(12,2),
    diferencia DECIMAL(12,2),
    -- Estado y metadata
    estado VARCHAR(20) NOT NULL DEFAULT 'ABIERTA',
    observaciones TEXT,
    fecha_apertura TIMESTAMP NOT NULL DEFAULT NOW(),
    fecha_cierre TIMESTAMP
);

CREATE INDEX idx_cajas_tenant_estado ON cajas(tenant_id, estado);
CREATE INDEX idx_cajas_tenant_usuario ON cajas(tenant_id, usuario_id);

-- Vincular ventas a caja (nullable para no romper datos existentes)
ALTER TABLE ventas ADD COLUMN IF NOT EXISTS caja_id BIGINT NULL REFERENCES cajas(id);
