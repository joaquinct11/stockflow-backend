-- Reemplaza el backfill simple de V78 con uno FEFO-inteligente:
--   Lotes vencidos  → stock_actual = 0                      (no se pueden vender)
--   Lotes vigentes  → distribuye el stock_actual real del producto
--                     en orden FEFO (el que vence antes absorbe primero)
--                     nunca supera la cantidad original ni el stock disponible restante.
-- Resultado: stockVigente total == producto.stockActual, sin exponer vencidos.

TRUNCATE TABLE stock_lotes;

INSERT INTO stock_lotes (tenant_id, movimiento_id, producto_id, sucursal_id, lote, fecha_vencimiento, stock_inicial, stock_actual, created_at)
SELECT
    m.tenant_id,
    m.id,
    m.producto_id,
    m.sucursal_id,
    m.lote,
    m.fecha_vencimiento,
    m.cantidad AS stock_inicial,
    CASE
        WHEN m.fecha_vencimiento < CURRENT_DATE THEN 0
        ELSE GREATEST(0,
            LEAST(
                m.cantidad,
                COALESCE(p.stock_actual, 0)
                - COALESCE((
                    SELECT SUM(m2.cantidad)
                    FROM movimientos_inventario m2
                    WHERE m2.producto_id    = m.producto_id
                      AND m2.tenant_id      = m.tenant_id
                      AND m2.tipo          IN ('ENTRADA', 'SALDO_INICIAL')
                      AND m2.fecha_vencimiento IS NOT NULL
                      AND m2.fecha_vencimiento >= CURRENT_DATE
                      AND (m2.fecha_vencimiento < m.fecha_vencimiento
                           OR (m2.fecha_vencimiento = m.fecha_vencimiento AND m2.id < m.id))
                ), 0)
            )
        )
    END AS stock_actual,
    COALESCE(m.created_at, NOW()) AS created_at
FROM movimientos_inventario m
JOIN productos p ON p.id = m.producto_id AND p.tenant_id = m.tenant_id
WHERE m.tipo IN ('ENTRADA', 'SALDO_INICIAL')
  AND m.fecha_vencimiento IS NOT NULL
ON CONFLICT (movimiento_id) DO NOTHING;
