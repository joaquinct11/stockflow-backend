-- V22: Facturación – comprobantes SUNAT (boleta/factura electrónica)
-- Creates comprobante_series (correlative tracker) and comprobantes tables,
-- adds new permissions for the Facturación module, and seeds default series
-- and permissions idempotently.

-- ── 1. comprobante_series ─────────────────────────────────────────────────────
CREATE TABLE comprobante_series (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(100) NOT NULL,
    tipo                VARCHAR(20)  NOT NULL,   -- BOLETA | FACTURA
    serie               VARCHAR(10)  NOT NULL,   -- e.g. B001, F001
    ultimo_correlativo  INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_comprobante_series UNIQUE (tenant_id, tipo, serie)
);

-- ── 2. comprobantes ───────────────────────────────────────────────────────────
CREATE TABLE comprobantes (
    id                   BIGSERIAL PRIMARY KEY,
    tenant_id            VARCHAR(100)   NOT NULL,
    venta_id             BIGINT         NOT NULL REFERENCES ventas(id),
    tipo                 VARCHAR(20)    NOT NULL,  -- BOLETA | FACTURA
    serie                VARCHAR(10)    NOT NULL,  -- e.g. B001
    correlativo          INTEGER        NOT NULL,
    numero               VARCHAR(30)    NOT NULL,  -- e.g. B001-00000001
    fecha_emision        TIMESTAMP      NOT NULL DEFAULT NOW(),
    estado               VARCHAR(20)    NOT NULL DEFAULT 'EMITIDO',  -- EMITIDO | ANULADO | ERROR
    subtotal             DECIMAL(10,2)  NOT NULL,
    igv                  DECIMAL(10,2)  NOT NULL,
    total                DECIMAL(10,2)  NOT NULL,
    -- Receptor
    receptor_doc_tipo    VARCHAR(10),   -- DNI | RUC
    receptor_doc_numero  VARCHAR(20),
    receptor_nombre      VARCHAR(200),
    receptor_direccion   VARCHAR(300),
    -- SUNAT placeholders
    sunat_estado         VARCHAR(20),   -- ACEPTADO | RECHAZADO | PENDIENTE
    sunat_ticket         VARCHAR(100),
    hash                 VARCHAR(200),
    qr                   TEXT,
    pdf_url              VARCHAR(500),
    xml_url              VARCHAR(500),
    created_at           TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP      NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_comprobante_numero  UNIQUE (tenant_id, tipo, serie, correlativo)
);

-- ── 3. Seed default series for existing tenants ───────────────────────────────
INSERT INTO comprobante_series (tenant_id, tipo, serie, ultimo_correlativo)
    SELECT t.tenant_id, 'BOLETA',  'B001', 0
    FROM tenants t
ON CONFLICT (tenant_id, tipo, serie) DO NOTHING;

INSERT INTO comprobante_series (tenant_id, tipo, serie, ultimo_correlativo)
    SELECT t.tenant_id, 'FACTURA', 'F001', 0
    FROM tenants t
ON CONFLICT (tenant_id, tipo, serie) DO NOTHING;

-- ── 4. New permissions for Facturación module ─────────────────────────────────
INSERT INTO permisos (nombre, descripcion, created_at) VALUES
    ('VER_FACTURACION',    'Permiso para acceder al módulo de facturación',      NOW()),
    ('EMITIR_COMPROBANTE', 'Permiso para emitir comprobantes (boleta/factura)',   NOW()),
    ('VER_COMPROBANTE',    'Permiso para ver el detalle de un comprobante',       NOW()),
    ('ANULAR_COMPROBANTE', 'Permiso para anular comprobantes emitidos',           NOW())
ON CONFLICT (nombre) DO NOTHING;
