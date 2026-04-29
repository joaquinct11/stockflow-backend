CREATE TABLE webhook_logs (
                              id BIGSERIAL PRIMARY KEY,
                              webhook_id VARCHAR(100) NOT NULL,
                              tipo VARCHAR(50) NOT NULL,
                              estado VARCHAR(20) NOT NULL,
                              fecha_procesamiento TIMESTAMP,
                              detalles TEXT,
                              CONSTRAINT uk_webhook_id_tipo UNIQUE (webhook_id, tipo)
);

CREATE INDEX idx_webhook_id_tipo ON webhook_logs(webhook_id, tipo);
CREATE INDEX idx_fecha_procesamiento ON webhook_logs(fecha_procesamiento DESC);