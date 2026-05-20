CREATE TABLE devoluciones (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(100) NOT NULL,
    venta_id BIGINT NOT NULL REFERENCES ventas(id),
    usuario_id BIGINT NOT NULL REFERENCES usuarios(id),
    motivo VARCHAR(200) NOT NULL,
    observaciones TEXT,
    total_devuelto DECIMAL(12,2) NOT NULL,
    reponer_stock BOOLEAN NOT NULL DEFAULT true,
    estado VARCHAR(20) NOT NULL DEFAULT 'PROCESADA',
    fecha_devolucion TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE devolucion_detalle (
    id BIGSERIAL PRIMARY KEY,
    devolucion_id BIGINT NOT NULL REFERENCES devoluciones(id),
    producto_id BIGINT NOT NULL REFERENCES productos(id),
    cantidad_devuelta INTEGER NOT NULL CHECK (cantidad_devuelta > 0),
    precio_unitario DECIMAL(10,2) NOT NULL,
    subtotal DECIMAL(10,2) NOT NULL
);

CREATE INDEX idx_devoluciones_tenant ON devoluciones(tenant_id);
CREATE INDEX idx_devoluciones_venta ON devoluciones(venta_id);
