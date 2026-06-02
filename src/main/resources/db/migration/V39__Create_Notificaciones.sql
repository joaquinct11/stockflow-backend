-- ============================================================
-- V39: Sistema de notificaciones in-app
-- ============================================================

CREATE TABLE notificaciones (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       VARCHAR(100)    NOT NULL,
    usuario_id      BIGINT,                         -- NULL = aplica a un rol completo
    rol_destino     VARCHAR(50),                    -- NULL = aplica a usuario específico
    tipo            VARCHAR(60)     NOT NULL,        -- STOCK_BAJO, PRODUCTO_VENCIDO, ...
    titulo          VARCHAR(255)    NOT NULL,
    mensaje         TEXT            NOT NULL,
    leida           BOOLEAN         NOT NULL DEFAULT false,
    referencia_id   BIGINT,                         -- ID del recurso relacionado
    referencia_tipo VARCHAR(50),                    -- PRODUCTO, VENTA, OC, CAJA, SUSCRIPCION, USUARIO
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- Índices para consultas frecuentes
CREATE INDEX idx_notif_tenant_rol    ON notificaciones (tenant_id, rol_destino, leida);
CREATE INDEX idx_notif_usuario       ON notificaciones (usuario_id, leida);
CREATE INDEX idx_notif_tipo_ref      ON notificaciones (tenant_id, tipo, referencia_id, leida);
CREATE INDEX idx_notif_created_at    ON notificaciones (created_at DESC);
