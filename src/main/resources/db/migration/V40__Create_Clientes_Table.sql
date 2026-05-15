-- ============================================================
-- V40: Tabla de clientes del negocio
-- ============================================================
-- Los clientes son por tenant (negocio). Se soportan múltiples
-- tipos de documento: DNI, RUC, CE, PASAPORTE.
-- Soft-delete mediante deleted_at; activo controla si aparece
-- en búsquedas y listas activas.

CREATE TABLE clientes (
    id                  BIGSERIAL       PRIMARY KEY,
    nombre              VARCHAR(200)    NOT NULL,
    tipo_documento      VARCHAR(20),                    -- DNI, RUC, CE, PASAPORTE
    numero_documento    VARCHAR(20),
    telefono            VARCHAR(20),
    email               VARCHAR(150),
    direccion           TEXT,
    activo              BOOLEAN         NOT NULL DEFAULT true,
    tenant_id           VARCHAR(100)    NOT NULL,
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMP
);

-- Índices
CREATE INDEX idx_clientes_tenant          ON clientes (tenant_id);
CREATE INDEX idx_clientes_tenant_activo   ON clientes (tenant_id, activo);
CREATE INDEX idx_clientes_nombre          ON clientes (tenant_id, LOWER(nombre));
CREATE INDEX idx_clientes_documento       ON clientes (tenant_id, numero_documento);
