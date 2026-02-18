CREATE TABLE productos (
                           id BIGSERIAL PRIMARY KEY,
                           nombre VARCHAR(255) NOT NULL,
                           codigo_barras VARCHAR(100) UNIQUE,
                           categoria VARCHAR(100),
                           stock_actual INTEGER DEFAULT 0,
                           stock_minimo INTEGER DEFAULT 10,
                           stock_maximo INTEGER DEFAULT 500,
                           costo_unitario NUMERIC(10, 2) NOT NULL,
                           precio_venta NUMERIC(10, 2) NOT NULL,
                           fecha_vencimiento DATE,
                           lote VARCHAR(50),
                           proveedor_id BIGINT,
                           activo BOOLEAN DEFAULT true,
                           fecha_creacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                           tenant_id VARCHAR(100),
                           created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_productos_codigo_barras ON productos(codigo_barras);
CREATE INDEX idx_productos_nombre ON productos(nombre);
CREATE INDEX idx_productos_tenant_id ON productos(tenant_id);
CREATE INDEX idx_productos_activo ON productos(activo);
CREATE INDEX idx_productos_stock_bajo ON productos(stock_actual) WHERE stock_actual < stock_minimo;