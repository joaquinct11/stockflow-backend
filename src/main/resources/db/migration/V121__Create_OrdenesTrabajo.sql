-- Unique constraint necesario para el compound FK de ordenes_trabajo → equipos_clientes
ALTER TABLE equipos_clientes
    ADD CONSTRAINT uq_equipo_id_tenant UNIQUE (id, tenant_id);

-- Tabla para generación concurrente-safe de numero_ot (un registro por tenant/año)
CREATE TABLE IF NOT EXISTS ot_numeracion (
    id        BIGSERIAL    PRIMARY KEY,
    tenant_id VARCHAR(100) NOT NULL,
    anio      SMALLINT     NOT NULL,
    ultimo    INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_ot_numeracion_tenant_anio UNIQUE (tenant_id, anio)
);

-- Tabla principal de órdenes de trabajo
CREATE TABLE IF NOT EXISTS ordenes_trabajo (
    id                    BIGSERIAL       PRIMARY KEY,
    tenant_id             VARCHAR(100)    NOT NULL,
    numero_ot             VARCHAR(50)     NOT NULL,
    anio                  SMALLINT        NOT NULL,
    equipo_cliente_id     BIGINT          NOT NULL,
    tecnico_id            BIGINT,
    sucursal_id           BIGINT          NOT NULL,
    cliente_id            BIGINT          NOT NULL,
    estado                VARCHAR(30)     NOT NULL DEFAULT 'ABIERTA',
    prioridad             VARCHAR(20)     NOT NULL DEFAULT 'NORMAL',
    descripcion_problema  TEXT            NOT NULL,
    diagnostico           TEXT,
    trabajo_realizado     TEXT,
    observaciones         TEXT,
    fecha_cierre_tecnico  TIMESTAMP,
    tecnico_cierre_id     BIGINT,
    venta_id              BIGINT,
    monto_mano_obra       NUMERIC(12,2),
    total_facturado       NUMERIC(12,2),
    created_at            TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP,
    created_by            BIGINT,
    updated_by            BIGINT,
    CONSTRAINT chk_ot_estado CHECK (estado IN (
        'ABIERTA','EN_PROCESO','DIAGNOSTICADA',
        'PENDIENTE_REPUESTO','CERRADA_TECN',
        'FACTURADA','ENTREGADA','CANCELADA'
    )),
    CONSTRAINT chk_ot_prioridad CHECK (prioridad IN ('BAJA','NORMAL','ALTA','URGENTE')),
    CONSTRAINT uq_ot_numero_tenant UNIQUE (tenant_id, numero_ot),
    CONSTRAINT fk_ot_equipo  FOREIGN KEY (equipo_cliente_id, tenant_id)
        REFERENCES equipos_clientes (id, tenant_id),
    CONSTRAINT fk_ot_tecnico FOREIGN KEY (tecnico_id) REFERENCES usuarios (id)
);

ALTER TABLE ordenes_trabajo
    ADD CONSTRAINT uq_ot_id_tenant UNIQUE (id, tenant_id);

CREATE INDEX IF NOT EXISTS idx_ot_tenant        ON ordenes_trabajo (tenant_id);
CREATE INDEX IF NOT EXISTS idx_ot_estado_tenant ON ordenes_trabajo (tenant_id, estado);
CREATE INDEX IF NOT EXISTS idx_ot_equipo        ON ordenes_trabajo (equipo_cliente_id, tenant_id);
CREATE INDEX IF NOT EXISTS idx_ot_cliente       ON ordenes_trabajo (tenant_id, cliente_id);
CREATE INDEX IF NOT EXISTS idx_ot_numero        ON ordenes_trabajo (tenant_id, numero_ot);
CREATE INDEX IF NOT EXISTS idx_ot_anio          ON ordenes_trabajo (tenant_id, anio);
CREATE INDEX IF NOT EXISTS idx_ot_tecnico       ON ordenes_trabajo (tenant_id, tecnico_id);

-- Historial de cambios de estado por OT
CREATE TABLE IF NOT EXISTS ot_historial_estados (
    id               BIGSERIAL    PRIMARY KEY,
    tenant_id        VARCHAR(100) NOT NULL,
    orden_trabajo_id BIGINT       NOT NULL,
    estado_anterior  VARCHAR(30),
    estado_nuevo     VARCHAR(30)  NOT NULL,
    usuario_id       BIGINT,
    observacion      TEXT,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_ot_historial_ot
        FOREIGN KEY (orden_trabajo_id, tenant_id)
        REFERENCES ordenes_trabajo (id, tenant_id)
);

CREATE INDEX IF NOT EXISTS idx_ot_hist_ot ON ot_historial_estados (orden_trabajo_id, tenant_id);
