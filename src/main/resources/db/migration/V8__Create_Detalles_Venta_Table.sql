CREATE TABLE detalles_venta (
                                id BIGSERIAL PRIMARY KEY,
                                venta_id BIGINT NOT NULL REFERENCES ventas(id) ON DELETE CASCADE,
                                producto_id BIGINT NOT NULL REFERENCES productos(id) ON DELETE CASCADE,
                                cantidad INTEGER NOT NULL,
                                precio_unitario NUMERIC(10, 2) NOT NULL,
                                subtotal NUMERIC(10, 2) NOT NULL,
                                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_detalles_venta_venta_id ON detalles_venta(venta_id);
CREATE INDEX idx_detalles_venta_producto_id ON detalles_venta(producto_id);