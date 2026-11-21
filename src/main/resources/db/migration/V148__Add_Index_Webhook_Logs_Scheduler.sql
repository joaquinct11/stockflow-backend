-- Índice parcial para las queries del scheduler de reconciliación Culqi.
-- Cubre: WHERE estado IN ('SIN_CORRELACION','PROCESADO_PARCIAL') AND intentos < N AND fecha_procesamiento >= ?
-- El índice parcial excluye filas en estados terminales (PROCESADO, IGNORADO, ERROR, PENDIENTE_REVISION)
-- que son la mayoría de las filas de la tabla a largo plazo.
CREATE INDEX IF NOT EXISTS idx_webhook_logs_scheduler
    ON webhook_logs (estado, intentos, fecha_procesamiento)
    WHERE estado IN ('SIN_CORRELACION', 'PROCESADO_PARCIAL');
