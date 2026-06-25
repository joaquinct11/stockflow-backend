CREATE TABLE certificados_establecimiento (
    id                BIGSERIAL PRIMARY KEY,
    tenant_id         VARCHAR(100) NOT NULL REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    tipo              VARCHAR(50)  NOT NULL,  -- CARNET_SANIDAD | FUMIGACION | EXTINTOR | INSTALACION_ELECTRICA | OTRO
    descripcion       VARCHAR(255) NOT NULL,  -- nombre del trabajador (carnet) o descripción del doc
    usuario_id        BIGINT       REFERENCES usuarios(id) ON DELETE SET NULL,  -- solo para CARNET_SANIDAD
    fecha_vencimiento DATE         NOT NULL,
    dias_alerta       INT          NOT NULL DEFAULT 30,
    observaciones     TEXT,
    activo            BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP
);

CREATE INDEX idx_cert_tenant_id        ON certificados_establecimiento(tenant_id);
CREATE INDEX idx_cert_fecha_venc       ON certificados_establecimiento(fecha_vencimiento);
CREATE INDEX idx_cert_tipo             ON certificados_establecimiento(tipo);
