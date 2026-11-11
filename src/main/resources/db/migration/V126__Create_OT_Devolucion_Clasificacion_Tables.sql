-- Fase 2B: Devoluciones y clasificaciones de consumos en órdenes de trabajo

-- Tabla principal de devoluciones de material consumido en OT
CREATE TABLE ot_consumo_devolucion (
    id               BIGSERIAL    PRIMARY KEY,
    tenant_id        VARCHAR(100) NOT NULL,
    consumo_id       BIGINT       NOT NULL REFERENCES ot_consumo_material(id),
    orden_trabajo_id BIGINT       NOT NULL REFERENCES ordenes_trabajo(id),
    cantidad_base    INTEGER      NOT NULL CHECK (cantidad_base > 0),
    motivo           TEXT,
    registrado_por   BIGINT,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ot_devolucion_consumo    ON ot_consumo_devolucion (consumo_id);
CREATE INDEX idx_ot_devolucion_ot_tenant  ON ot_consumo_devolucion (orden_trabajo_id, tenant_id);
CREATE INDEX idx_ot_devolucion_tenant     ON ot_consumo_devolucion (tenant_id);

-- Trazabilidad de lotes restituidos en cada devolución
CREATE TABLE ot_consumo_devolucion_lote (
    id            BIGSERIAL PRIMARY KEY,
    devolucion_id BIGINT    NOT NULL REFERENCES ot_consumo_devolucion(id),
    stock_lote_id BIGINT    NOT NULL REFERENCES stock_lotes(id),
    cantidad      INTEGER   NOT NULL CHECK (cantidad > 0),
    created_at    TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ot_devolucion_lote_devolucion ON ot_consumo_devolucion_lote (devolucion_id);

-- Clasificación del estado de materiales consumidos en OT
CREATE TABLE ot_consumo_clasificacion (
    id               BIGSERIAL    PRIMARY KEY,
    tenant_id        VARCHAR(100) NOT NULL,
    consumo_id       BIGINT       NOT NULL REFERENCES ot_consumo_material(id),
    orden_trabajo_id BIGINT       NOT NULL REFERENCES ordenes_trabajo(id),
    clasificacion    VARCHAR(20)  NOT NULL CHECK (clasificacion IN ('INSTALADO','RECUPERABLE','DAÑADO','PERDIDO')),
    cantidad         INTEGER      NOT NULL CHECK (cantidad > 0),
    notas            TEXT,
    registrado_por   BIGINT,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ot_clasificacion_consumo   ON ot_consumo_clasificacion (consumo_id);
CREATE INDEX idx_ot_clasificacion_ot_tenant ON ot_consumo_clasificacion (orden_trabajo_id, tenant_id);
CREATE INDEX idx_ot_clasificacion_tenant    ON ot_consumo_clasificacion (tenant_id);
