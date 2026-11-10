-- Fase 2E: columnas de entrega del equipo en la OT (1:1 con la orden de trabajo)
ALTER TABLE ordenes_trabajo
    ADD COLUMN IF NOT EXISTS recibido_por         VARCHAR(200),
    ADD COLUMN IF NOT EXISTS documento_receptor   VARCHAR(50),
    ADD COLUMN IF NOT EXISTS fecha_entrega        TIMESTAMP,
    ADD COLUMN IF NOT EXISTS entregado_por        BIGINT,
    ADD COLUMN IF NOT EXISTS observaciones_entrega TEXT,
    ADD COLUMN IF NOT EXISTS conformidad_cliente  BOOLEAN;
