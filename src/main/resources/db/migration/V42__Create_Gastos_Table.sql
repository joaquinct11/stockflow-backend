-- ============================================================
-- V42: Módulo de Gastos / Egresos
-- ============================================================
-- Registra todos los gastos operativos del negocio por tenant:
-- alquiler, servicios, sueldos, mantenimiento, publicidad, etc.
-- Soft-delete mediante deleted_at.

CREATE TABLE gastos (
    id                  BIGSERIAL       PRIMARY KEY,
    concepto            VARCHAR(300)    NOT NULL,
    categoria           VARCHAR(30)     NOT NULL,       -- ALQUILER, SERVICIOS, SUELDOS, etc.
    monto               NUMERIC(12,2)   NOT NULL CHECK (monto > 0),
    fecha_gasto         DATE            NOT NULL,
    metodo_pago         VARCHAR(20),                    -- EFECTIVO, TRANSFERENCIA, TARJETA, YAPE, PLIN, CHEQUE, OTRO
    numero_comprobante  VARCHAR(50),
    notas               TEXT,
    activo              BOOLEAN         NOT NULL DEFAULT true,
    tenant_id           VARCHAR(100)    NOT NULL,
    registrado_por      VARCHAR(150),
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMP
);

-- Índices para reportes y filtros frecuentes
CREATE INDEX idx_gastos_tenant            ON gastos (tenant_id);
CREATE INDEX idx_gastos_tenant_fecha      ON gastos (tenant_id, fecha_gasto DESC);
CREATE INDEX idx_gastos_tenant_categoria  ON gastos (tenant_id, categoria);
CREATE INDEX idx_gastos_deleted_at        ON gastos (deleted_at) WHERE deleted_at IS NULL;
