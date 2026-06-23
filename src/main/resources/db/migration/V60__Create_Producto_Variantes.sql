CREATE TABLE IF NOT EXISTS producto_variantes (
    id           BIGSERIAL PRIMARY KEY,
    producto_id  BIGINT       NOT NULL REFERENCES productos(id) ON DELETE CASCADE,
    talla        VARCHAR(50),
    color        VARCHAR(100),
    stock_actual INTEGER      NOT NULL DEFAULT 0,
    stock_minimo INTEGER      NOT NULL DEFAULT 0,
    sku          VARCHAR(100),
    activo       BOOLEAN      NOT NULL DEFAULT TRUE,
    tenant_id    VARCHAR(255),
    created_at   TIMESTAMP    DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_pv_producto_id ON producto_variantes(producto_id);
CREATE INDEX IF NOT EXISTS idx_pv_tenant_id   ON producto_variantes(tenant_id);
