-- ============================================================
-- V104: Módulo Veterinaria — tabla mascotas
-- ============================================================
-- Una mascota pertenece a un cliente del mismo tenant.
-- La FK compuesta (cliente_id, tenant_id) → clientes(id, tenant_id)
-- garantiza a nivel de base de datos que una mascota no puede
-- apuntar a un cliente de otro tenant.

-- 1. Clave alternativa en clientes para soportar FK compuesta
ALTER TABLE clientes
    ADD CONSTRAINT uq_clientes_id_tenant UNIQUE (id, tenant_id);

-- 2. Tabla mascotas
CREATE TABLE mascotas (
    id               BIGSERIAL        PRIMARY KEY,
    tenant_id        VARCHAR(100)     NOT NULL
                         REFERENCES tenants(tenant_id) ON DELETE RESTRICT,
    cliente_id       BIGINT           NOT NULL,
    nombre           VARCHAR(100)     NOT NULL,
    especie          VARCHAR(50)      NOT NULL,
    raza             VARCHAR(100),
    sexo             VARCHAR(10)      CHECK (sexo IN ('MACHO', 'HEMBRA', 'DESCONOCIDO')),
    fecha_nacimiento DATE,
    color            VARCHAR(100),
    peso_kg          DECIMAL(5,2)     CHECK (peso_kg IS NULL OR peso_kg > 0),
    microchip        VARCHAR(50),
    esterilizado     BOOLEAN,
    activo           BOOLEAN          NOT NULL DEFAULT true,
    created_at       TIMESTAMP        NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP,

    CONSTRAINT fk_mascotas_cliente_tenant
        FOREIGN KEY (cliente_id, tenant_id)
        REFERENCES clientes (id, tenant_id)
        ON DELETE RESTRICT
);

-- 3. Índices
CREATE INDEX idx_mascotas_tenant        ON mascotas (tenant_id);
CREATE INDEX idx_mascotas_tenant_activo ON mascotas (tenant_id, activo);
CREATE INDEX idx_mascotas_cliente       ON mascotas (tenant_id, cliente_id);
CREATE INDEX idx_mascotas_nombre        ON mascotas (tenant_id, LOWER(nombre));
CREATE INDEX idx_mascotas_especie       ON mascotas (tenant_id, especie);

-- Microchip único por tenant (solo cuando tiene valor)
CREATE UNIQUE INDEX uq_mascotas_microchip
    ON mascotas (tenant_id, microchip)
    WHERE microchip IS NOT NULL;
