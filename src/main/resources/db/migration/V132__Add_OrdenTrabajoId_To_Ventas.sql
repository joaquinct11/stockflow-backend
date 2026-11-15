-- V132: Añade orden_trabajo_id a ventas para enlace bi-direccional OT ↔ Venta
-- Permite distinguir ventas generadas por OT de ventas normales (fix C1: anularVenta).

ALTER TABLE ventas
    ADD COLUMN IF NOT EXISTS orden_trabajo_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname   = 'fk_venta_orden_trabajo'
          AND conrelid  = 'ventas'::regclass
    ) THEN
        ALTER TABLE ventas
            ADD CONSTRAINT fk_venta_orden_trabajo
                FOREIGN KEY (orden_trabajo_id) REFERENCES ordenes_trabajo(id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_ventas_orden_trabajo_id
    ON ventas(orden_trabajo_id)
    WHERE orden_trabajo_id IS NOT NULL;
