-- V23: Orden de Compra + Recepción
-- Creates tables for purchase orders (orden_compra / orden_compra_detalle)
-- and goods receipt (recepcion / recepcion_detalle), and registers the new
-- permission codes. All inserts are idempotent (ON CONFLICT DO NOTHING).

-- ── 1. orden_compra ──────────────────────────────────────────────────────────
CREATE TABLE orden_compra (
    id                  BIGSERIAL    PRIMARY KEY,
    tenant_id           VARCHAR(100) NOT NULL,
    proveedor_id        BIGINT       NOT NULL REFERENCES proveedores(id),
    usuario_creador_id  BIGINT       NOT NULL REFERENCES usuarios(id),
    estado              VARCHAR(20)  NOT NULL DEFAULT 'BORRADOR',
                                     -- BORRADOR | ENVIADA | RECIBIDA_PARCIAL | RECIBIDA | CANCELADA
    observaciones       TEXT,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_oc_tenant     ON orden_compra (tenant_id);
CREATE INDEX idx_oc_proveedor  ON orden_compra (proveedor_id);
CREATE INDEX idx_oc_estado     ON orden_compra (estado);

-- ── 2. orden_compra_detalle ──────────────────────────────────────────────────
CREATE TABLE orden_compra_detalle (
    id                  BIGSERIAL       PRIMARY KEY,
    orden_compra_id     BIGINT          NOT NULL REFERENCES orden_compra(id) ON DELETE CASCADE,
    producto_id         BIGINT          NOT NULL REFERENCES productos(id),
    cantidad_solicitada INTEGER         NOT NULL CHECK (cantidad_solicitada > 0),
    precio_unitario     DECIMAL(10,2)
);

CREATE INDEX idx_ocd_oc       ON orden_compra_detalle (orden_compra_id);
CREATE INDEX idx_ocd_producto ON orden_compra_detalle (producto_id);

-- ── 3. recepcion ─────────────────────────────────────────────────────────────
CREATE TABLE recepcion (
    id                  BIGSERIAL    PRIMARY KEY,
    tenant_id           VARCHAR(100) NOT NULL,
    oc_id               BIGINT       REFERENCES orden_compra(id),   -- nullable: recepción sin OC
    proveedor_id        BIGINT       NOT NULL REFERENCES proveedores(id),
    usuario_receptor_id BIGINT       NOT NULL REFERENCES usuarios(id),
    estado              VARCHAR(20)  NOT NULL DEFAULT 'BORRADOR',
                                     -- BORRADOR | CONFIRMADA
    -- Comprobante del proveedor (factura/boleta física)
    tipo_comprobante    VARCHAR(10),  -- FACTURA | BOLETA
    serie               VARCHAR(20),
    numero              VARCHAR(30),
    url_adjunto         VARCHAR(500),
    observaciones       TEXT,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    fecha_confirmacion  TIMESTAMP
);

CREATE INDEX idx_rec_tenant    ON recepcion (tenant_id);
CREATE INDEX idx_rec_oc        ON recepcion (oc_id);
CREATE INDEX idx_rec_proveedor ON recepcion (proveedor_id);
CREATE INDEX idx_rec_estado    ON recepcion (estado);

-- ── 4. recepcion_detalle ─────────────────────────────────────────────────────
CREATE TABLE recepcion_detalle (
    id                  BIGSERIAL       PRIMARY KEY,
    recepcion_id        BIGINT          NOT NULL REFERENCES recepcion(id) ON DELETE CASCADE,
    producto_id         BIGINT          NOT NULL REFERENCES productos(id),
    cantidad_recibida   INTEGER         NOT NULL CHECK (cantidad_recibida > 0),
    fecha_vencimiento   DATE
);

CREATE INDEX idx_recd_recepcion ON recepcion_detalle (recepcion_id);
CREATE INDEX idx_recd_producto  ON recepcion_detalle (producto_id);

-- ── 5. New permission codes ───────────────────────────────────────────────────
INSERT INTO permisos (nombre, descripcion, created_at) VALUES
    ('VER_OC',              'Permiso para listar órdenes de compra',            NOW()),
    ('CREAR_OC',            'Permiso para crear órdenes de compra',             NOW()),
    ('EDITAR_OC',           'Permiso para editar órdenes de compra',            NOW()),
    ('VER_RECEPCIONES',     'Permiso para listar recepciones de mercadería',    NOW()),
    ('CREAR_RECEPCION',     'Permiso para crear recepciones de mercadería',     NOW()),
    ('CONFIRMAR_RECEPCION', 'Permiso para confirmar recepciones (impacta inventario)', NOW())
ON CONFLICT (nombre) DO NOTHING;
