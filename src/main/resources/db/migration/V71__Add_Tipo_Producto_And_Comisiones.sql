-- ============================================================
-- V71: Soporte rubro EMPRESA_SERVICIOS
-- ============================================================
-- 1. Campo tipo en productos: PRODUCTO (default) | SERVICIO
--    Los servicios no descuentan stock al venderse.
-- 2. Tabla comisiones: ingresos recibidos de terceros (ej: Bitel).
-- ============================================================

-- ── 1. Tipo de producto ──────────────────────────────────────
ALTER TABLE productos
    ADD COLUMN IF NOT EXISTS tipo VARCHAR(20) NOT NULL DEFAULT 'PRODUCTO';

-- Índice para filtrar fácilmente servicios
CREATE INDEX IF NOT EXISTS idx_productos_tipo ON productos(tipo);

-- ── 2. Tabla comisiones ──────────────────────────────────────
CREATE TABLE IF NOT EXISTS comisiones (
    id                  BIGSERIAL       PRIMARY KEY,
    tenant_id           VARCHAR(100)    NOT NULL,
    concepto            VARCHAR(300)    NOT NULL,
    pagador             VARCHAR(200)    NOT NULL,   -- Ej: "Bitel", "Claro"
    monto               NUMERIC(12, 2)  NOT NULL CHECK (monto > 0),
    fecha               DATE            NOT NULL,
    metodo_pago         VARCHAR(20),                -- EFECTIVO | TRANSFERENCIA | OTRO
    numero_comprobante  VARCHAR(100),               -- Referencia de la factura/nota que emitieron
    notas               TEXT,
    registrado_por      VARCHAR(150),
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_comisiones_tenant       ON comisiones(tenant_id);
CREATE INDEX IF NOT EXISTS idx_comisiones_tenant_fecha ON comisiones(tenant_id, fecha DESC);
CREATE INDEX IF NOT EXISTS idx_comisiones_pagador      ON comisiones(tenant_id, pagador);

-- ── 3. Permisos de comisiones ────────────────────────────────
INSERT INTO permisos (nombre, descripcion, created_at) VALUES
    ('VER_COMISIONES',    'Ver listado de comisiones recibidas',      NOW()),
    ('CREAR_COMISION',    'Registrar una nueva comisión',             NOW()),
    ('EDITAR_COMISION',   'Editar una comisión existente',            NOW()),
    ('ELIMINAR_COMISION', 'Eliminar una comisión',                    NOW())
ON CONFLICT (nombre) DO NOTHING;
