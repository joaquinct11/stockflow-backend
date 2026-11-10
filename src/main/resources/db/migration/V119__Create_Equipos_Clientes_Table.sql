CREATE TABLE equipos_clientes (
    id             BIGSERIAL    PRIMARY KEY,
    tenant_id      VARCHAR(100) NOT NULL,
    cliente_id     BIGINT       NOT NULL,
    nombre         VARCHAR(255) NOT NULL,
    tipo           VARCHAR(100),
    marca          VARCHAR(100),
    modelo         VARCHAR(100),
    numero_serie   VARCHAR(100),
    ubicacion      VARCHAR(255),
    observaciones  TEXT,
    activo         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP,
    CONSTRAINT fk_equipo_cliente
        FOREIGN KEY (cliente_id, tenant_id)
        REFERENCES clientes (id, tenant_id),
    CONSTRAINT uq_equipo_serie_tenant
        UNIQUE (tenant_id, numero_serie)
);

CREATE INDEX idx_equipos_tenant  ON equipos_clientes (tenant_id);
CREATE INDEX idx_equipos_cliente ON equipos_clientes (cliente_id, tenant_id);
CREATE INDEX idx_equipos_activo  ON equipos_clientes (tenant_id, activo);
