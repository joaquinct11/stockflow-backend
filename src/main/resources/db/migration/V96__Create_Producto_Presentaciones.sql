-- Presentaciones adicionales de un producto (multi-unidad para farmacia/botica)
-- Permite vender un mismo producto en distintas unidades con distintos precios.
-- Ej: Paracetamol 500mg → CAJA S/8.00, BLISTER S/2.50, TABLETA S/0.30

CREATE TABLE producto_presentaciones (
    id                BIGSERIAL PRIMARY KEY,
    producto_id       BIGINT        NOT NULL REFERENCES productos(id) ON DELETE CASCADE,
    unidad_medida_id  BIGINT        NOT NULL REFERENCES unidad_medida(id),
    precio_venta      NUMERIC(10,2) NOT NULL CHECK (precio_venta > 0),
    factor            INTEGER       NOT NULL DEFAULT 1 CHECK (factor >= 1),
    es_principal      BOOLEAN       NOT NULL DEFAULT FALSE,
    tenant_id         VARCHAR(50),

    CONSTRAINT uq_presentacion_producto_unidad UNIQUE (producto_id, unidad_medida_id)
);

CREATE INDEX idx_presentaciones_producto ON producto_presentaciones(producto_id);
CREATE INDEX idx_presentaciones_tenant   ON producto_presentaciones(tenant_id);
