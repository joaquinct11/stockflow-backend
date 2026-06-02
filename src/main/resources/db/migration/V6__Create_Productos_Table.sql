CREATE TABLE productos (
                           id BIGSERIAL PRIMARY KEY,
                           nombre VARCHAR(255) NOT NULL,
                           codigo_barras VARCHAR(100) UNIQUE,
                           imagen_url TEXT,
                           categoria VARCHAR(100),
                           stock_actual INTEGER DEFAULT 0,
                           stock_minimo INTEGER DEFAULT 10,
                           stock_maximo INTEGER DEFAULT 100,
                           costo_unitario NUMERIC(10, 2) NOT NULL,
                           precio_venta NUMERIC(10, 2) NOT NULL,
                           unidad_medida_id BIGINT NOT NULL REFERENCES unidad_medida(id),
                           activo BOOLEAN DEFAULT true,
                           deleted_at TIMESTAMP DEFAULT NULL,
                           tenant_id VARCHAR(100) NOT NULL REFERENCES tenants(tenant_id) ON DELETE CASCADE,
                           created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_productos_codigo_barras ON productos(codigo_barras);
CREATE INDEX idx_productos_nombre ON productos(nombre);
CREATE INDEX idx_productos_tenant_id ON productos(tenant_id);
CREATE INDEX idx_productos_activo ON productos(activo);
CREATE INDEX idx_productos_deleted_at ON productos(deleted_at);
CREATE INDEX idx_productos_stock_bajo ON productos(stock_actual) WHERE stock_actual < stock_minimo;