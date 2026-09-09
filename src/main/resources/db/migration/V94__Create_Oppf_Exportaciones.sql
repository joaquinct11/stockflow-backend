CREATE TABLE oppf_exportaciones (
    id                   BIGSERIAL PRIMARY KEY,
    tenant_id            VARCHAR(100)  NOT NULL,
    ruc                  VARCHAR(20)   NOT NULL,
    cod_establecimiento  VARCHAR(50)   NOT NULL,
    mes                  VARCHAR(2)    NOT NULL,
    ano                  VARCHAR(4)    NOT NULL,
    tipo                 VARCHAR(50)   NOT NULL DEFAULT 'CARGA ARCHIVO',
    total_productos      INT           NOT NULL,
    nombre_archivo       VARCHAR(200)  NOT NULL,
    created_at           TIMESTAMP     NOT NULL DEFAULT now()
);

CREATE INDEX idx_oppf_exportaciones_tenant ON oppf_exportaciones (tenant_id, created_at DESC);
