-- V137: Tabla principal de Guías de Remisión Electrónicas (GRE).
-- Separa estado_tributario (SUNAT) del estado_logistico (movimiento físico).
-- venta_id es nullable porque los traslados entre sucursales no tienen venta asociada.

CREATE TABLE guias_remision (
    id                        BIGSERIAL PRIMARY KEY,
    tenant_id                 VARCHAR(100) NOT NULL REFERENCES tenants(tenant_id),
    serie                     VARCHAR(10)  NOT NULL,
    correlativo               INTEGER      NOT NULL,
    tipo_traslado             VARCHAR(20)  NOT NULL,         -- SUCURSALES | DELIVERY
    motivo_traslado_codigo    VARCHAR(10)  NOT NULL,         -- Catálogo 20 SUNAT (ej: '04')
    motivo_traslado_descripcion VARCHAR(200),
    modalidad_transporte      VARCHAR(10)  NOT NULL,         -- PRIVADO | PUBLICO
    fecha_traslado            DATE         NOT NULL,
    peso_bruto_total          NUMERIC(12,3),
    unidad_medida_peso        VARCHAR(10)  DEFAULT 'KGM',
    numero_bultos             INTEGER,
    observaciones             VARCHAR(500),

    -- Origen y destino
    sucursal_origen_id        BIGINT REFERENCES sucursales(id),
    sucursal_destino_id       BIGINT REFERENCES sucursales(id),  -- nullable para delivery

    -- Destinatario (para delivery a cliente)
    destinatario_tipo_doc     VARCHAR(5),   -- '1' DNI, '6' RUC
    destinatario_num_doc      VARCHAR(20),
    destinatario_razon_social VARCHAR(255),
    destinatario_direccion    VARCHAR(500),

    -- Venta relacionada (solo para delivery; NULL para traslados entre sucursales)
    venta_id                  BIGINT REFERENCES ventas(id),

    -- Estados separados
    estado_tributario         VARCHAR(20)  NOT NULL DEFAULT 'BORRADOR',   -- BORRADOR | EMITIENDO | EMITIDA | ERROR | CANCELADA
    estado_logistico          VARCHAR(20)  NOT NULL DEFAULT 'PENDIENTE',  -- PENDIENTE | EN_TRANSITO | RECIBIDA | CANCELADA

    -- Respuesta Lucode
    lucode_id                 VARCHAR(100),
    lucode_hash               VARCHAR(255),
    xml_url                   VARCHAR(1000),
    cdr_url                   VARCHAR(1000),
    pdf_url                   VARCHAR(1000),
    lucode_respuesta_raw      TEXT,
    lucode_error_raw          TEXT,

    -- Idempotencia: evitar doble emisión ante reintentos
    idempotency_key           VARCHAR(255) UNIQUE,

    -- Auditoría
    created_by                VARCHAR(100),
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_guia_serie_correlativo UNIQUE (tenant_id, serie, correlativo)
);

CREATE INDEX idx_gr_tenant          ON guias_remision(tenant_id);
CREATE INDEX idx_gr_tenant_estado_t ON guias_remision(tenant_id, estado_tributario);
CREATE INDEX idx_gr_tenant_estado_l ON guias_remision(tenant_id, estado_logistico);
CREATE INDEX idx_gr_venta           ON guias_remision(venta_id);
CREATE INDEX idx_gr_fecha           ON guias_remision(fecha_traslado);
