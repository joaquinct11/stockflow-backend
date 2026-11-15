-- ============================================================
-- V107: Módulo Veterinaria — tabla citas
-- ============================================================

CREATE TABLE citas (
    id                BIGSERIAL        PRIMARY KEY,
    tenant_id         VARCHAR(100)     NOT NULL,
    sucursal_id       BIGINT,
    cliente_id        BIGINT           NOT NULL,
    mascota_id        BIGINT           NOT NULL,
    usuario_tenant_id BIGINT,
    creado_por_id     BIGINT,
    fecha_inicio      TIMESTAMP        NOT NULL,
    fecha_fin         TIMESTAMP        NOT NULL,
    tipo              VARCHAR(30)      NOT NULL
                          CHECK (tipo IN ('CONSULTA','VACUNACION','DESPARASITACION','CONTROL','PELUQUERIA','OTRO')),
    motivo            TEXT,
    estado            VARCHAR(20)      NOT NULL DEFAULT 'PENDIENTE'
                          CHECK (estado IN ('PENDIENTE','CONFIRMADA','EN_ATENCION','ATENDIDA','CANCELADA','NO_ASISTIO')),
    observaciones     TEXT,
    created_at        TIMESTAMP        NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP        NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_citas_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id) ON DELETE RESTRICT,

    CONSTRAINT fk_citas_cliente
        FOREIGN KEY (cliente_id) REFERENCES clientes(id) ON DELETE RESTRICT,

    CONSTRAINT fk_citas_mascota
        FOREIGN KEY (mascota_id) REFERENCES mascotas(id) ON DELETE RESTRICT,

    CONSTRAINT fk_citas_profesional
        FOREIGN KEY (usuario_tenant_id) REFERENCES usuario_tenant(id) ON DELETE SET NULL,

    CONSTRAINT fk_citas_creado_por
        FOREIGN KEY (creado_por_id) REFERENCES usuario_tenant(id) ON DELETE SET NULL,

    CONSTRAINT chk_citas_fechas
        CHECK (fecha_fin > fecha_inicio)
);

-- Índices
CREATE INDEX idx_citas_tenant        ON citas (tenant_id);
CREATE INDEX idx_citas_estado        ON citas (tenant_id, estado);
CREATE INDEX idx_citas_fecha         ON citas (tenant_id, fecha_inicio);
CREATE INDEX idx_citas_cliente       ON citas (tenant_id, cliente_id);
CREATE INDEX idx_citas_mascota       ON citas (tenant_id, mascota_id);
CREATE INDEX idx_citas_profesional   ON citas (tenant_id, usuario_tenant_id);
