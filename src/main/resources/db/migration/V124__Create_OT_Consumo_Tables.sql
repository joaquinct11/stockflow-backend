-- Fase 2A: Consumo de materiales y trazabilidad por lotes en OT

-- ── ot_consumo_material ───────────────────────────────────────────────────────
-- Un registro por cada ítem de material consumido en una OT.
-- Soporta presentaciones (factor) y productos de tipo SERVICIO (sin descuento de stock).
CREATE TABLE ot_consumo_material (
    id                    BIGSERIAL       PRIMARY KEY,
    tenant_id             VARCHAR(100)    NOT NULL,
    orden_trabajo_id      BIGINT          NOT NULL,
    producto_id           BIGINT          NOT NULL,
    presentacion_id       BIGINT,
    factor_presentacion   INTEGER         NOT NULL DEFAULT 1,
    cantidad_presentacion INTEGER         NOT NULL,
    cantidad_base         INTEGER         NOT NULL,
    precio_unitario       NUMERIC(12, 2),
    notas                 TEXT,
    registrado_por        BIGINT,
    created_at            TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_cm_cantidad_base CHECK (cantidad_base > 0),
    CONSTRAINT chk_cm_cantidad_pres CHECK (cantidad_presentacion > 0),
    CONSTRAINT chk_cm_factor        CHECK (factor_presentacion > 0),

    CONSTRAINT fk_cm_ot   FOREIGN KEY (orden_trabajo_id) REFERENCES ordenes_trabajo (id),
    CONSTRAINT fk_cm_prod FOREIGN KEY (producto_id)      REFERENCES productos (id)
);

CREATE INDEX idx_ot_cm_ot_tenant  ON ot_consumo_material (orden_trabajo_id, tenant_id);
CREATE INDEX idx_ot_cm_producto   ON ot_consumo_material (producto_id,      tenant_id);
CREATE INDEX idx_ot_cm_created_at ON ot_consumo_material (created_at DESC);

-- ── ot_consumo_lote ───────────────────────────────────────────────────────────
-- Trazabilidad por lote: una fila por cada lote que aportó unidades al consumo.
-- Ausencia de filas indica que el producto no usa control por lotes.
CREATE TABLE ot_consumo_lote (
    id            BIGSERIAL     PRIMARY KEY,
    consumo_id    BIGINT        NOT NULL,
    tenant_id     VARCHAR(100)  NOT NULL,
    producto_id   BIGINT        NOT NULL,
    stock_lote_id BIGINT        NOT NULL,
    cantidad      INTEGER       NOT NULL,
    created_at    TIMESTAMP     NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_cl_cantidad CHECK (cantidad > 0),

    CONSTRAINT fk_cl_consumo  FOREIGN KEY (consumo_id)    REFERENCES ot_consumo_material (id),
    CONSTRAINT fk_cl_lote     FOREIGN KEY (stock_lote_id) REFERENCES stock_lotes (id),
    CONSTRAINT fk_cl_producto FOREIGN KEY (producto_id)   REFERENCES productos (id)
);

CREATE INDEX idx_ot_cl_consumo ON ot_consumo_lote (consumo_id);
CREATE INDEX idx_ot_cl_lote    ON ot_consumo_lote (stock_lote_id);
