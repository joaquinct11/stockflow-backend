-- Añade contadores de reintento para la reconciliación interna de eventos SIN_CORRELACION.
-- El scheduler consulta solo eventos con intentos < MAX y fechaProcesamiento reciente.

ALTER TABLE webhook_logs
    ADD COLUMN IF NOT EXISTS intentos INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS ultimo_reintento TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_webhook_logs_estado_intentos
    ON webhook_logs (estado, intentos);
