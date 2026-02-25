CREATE TABLE suscripciones (
                               id BIGSERIAL PRIMARY KEY,
                               usuario_principal_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE RESTRICT,
                               plan_id VARCHAR(50) NOT NULL,
                               precio_mensual NUMERIC(10, 2) NOT NULL,
                               preapproval_id VARCHAR(255),
                               estado VARCHAR(50) DEFAULT 'ACTIVA',
                               deleted_at TIMESTAMP DEFAULT NULL,
                               fecha_inicio TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                               fecha_proximo_cobro TIMESTAMP,
                               intentos_fallidos INTEGER DEFAULT 0,
                               fecha_cancelacion TIMESTAMP,
                               metodo_pago VARCHAR(50),
                               ultimos_4_digitos VARCHAR(4),
                               tenant_id VARCHAR(100) NOT NULL REFERENCES tenants(tenant_id) ON DELETE CASCADE,
                               created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_suscripciones_usuario_id ON suscripciones(usuario_principal_id);
CREATE INDEX idx_suscripciones_estado ON suscripciones(estado);
CREATE INDEX idx_suscripciones_deleted_at ON suscripciones(deleted_at);
CREATE INDEX idx_suscripciones_fecha_proximo_cobro ON suscripciones(fecha_proximo_cobro);
CREATE INDEX idx_suscripciones_tenant_id ON suscripciones(tenant_id);
CREATE INDEX idx_suscripciones_usuario_tenant ON suscripciones(usuario_principal_id, tenant_id);