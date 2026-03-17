CREATE TABLE movimientos_inventario (
                                        id BIGSERIAL PRIMARY KEY,
                                        producto_id BIGINT NOT NULL REFERENCES productos(id) ON DELETE CASCADE,
                                        cantidad INTEGER NOT NULL,
                                        tipo VARCHAR(50) NOT NULL,
                                        usuario_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
                                        descripcion TEXT,
                                        tenant_id VARCHAR(100) NOT NULL REFERENCES tenants(tenant_id) ON DELETE CASCADE,
                                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_movimientos_producto_id ON movimientos_inventario(producto_id);
CREATE INDEX idx_movimientos_tipo ON movimientos_inventario(tipo);
CREATE INDEX idx_movimientos_tenant_id ON movimientos_inventario(tenant_id);
CREATE INDEX idx_movimientos_fecha ON movimientos_inventario(created_at);
CREATE INDEX idx_movimientos_usuario_id ON movimientos_inventario(usuario_id);