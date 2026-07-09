CREATE TABLE IF NOT EXISTS stock_lotes (
    id                BIGSERIAL PRIMARY KEY,
    tenant_id         VARCHAR(255) NOT NULL,
    movimiento_id     BIGINT       NOT NULL,
    producto_id       BIGINT       NOT NULL,
    sucursal_id       BIGINT,
    lote              VARCHAR(100),
    fecha_vencimiento DATE         NOT NULL,
    stock_inicial     INTEGER      NOT NULL,
    stock_actual      INTEGER      NOT NULL,
    created_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP,
    CONSTRAINT uk_stock_lotes_movimiento UNIQUE (movimiento_id)
);

CREATE INDEX IF NOT EXISTS idx_stock_lotes_producto    ON stock_lotes (producto_id, tenant_id);
CREATE INDEX IF NOT EXISTS idx_stock_lotes_vencimiento ON stock_lotes (fecha_vencimiento);

-- Backfill desde movimientos existentes:
--   Lotes vencidos  → stock_actual = 0
--   Lotes vigentes  → stock_actual = cantidad original (punto de partida conservador)
INSERT INTO stock_lotes (tenant_id, movimiento_id, producto_id, sucursal_id, lote, fecha_vencimiento, stock_inicial, stock_actual, created_at)
SELECT
    m.tenant_id,
    m.id                                                         AS movimiento_id,
    m.producto_id,
    m.sucursal_id,
    m.lote,
    m.fecha_vencimiento,
    m.cantidad                                                   AS stock_inicial,
    CASE WHEN m.fecha_vencimiento < CURRENT_DATE THEN 0
         ELSE m.cantidad
    END                                                          AS stock_actual,
    COALESCE(m.created_at, NOW())                                AS created_at
FROM movimientos_inventario m
WHERE m.tipo IN ('ENTRADA', 'SALDO_INICIAL')
  AND m.fecha_vencimiento IS NOT NULL
ON CONFLICT (movimiento_id) DO NOTHING;
