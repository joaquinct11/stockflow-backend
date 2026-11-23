-- =============================================================================
-- F-13: Diagnóstico de Inconsistencias de Stock en Devoluciones
-- StockFlow Backend — Solo lectura, sin modificaciones
-- Reemplazar :tenantId con el tenant correspondiente.
-- Reemplazar :productoId con el ID del producto a auditar.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. Resumen de discrepancias: productos cuyo stock_actual difiere
--    del saldo calculado por Kardex (ENTRADA + DEVOLUCION - SALIDA - MERMA)
--    Nota: AJUSTE es set absoluto; se considera como reset del saldo.
-- ---------------------------------------------------------------------------
WITH kardex_saldo AS (
    SELECT
        m.producto_id,
        SUM(CASE
            WHEN m.tipo IN ('ENTRADA', 'DEVOLUCION')    THEN  m.cantidad
            WHEN m.tipo IN ('SALIDA',  'MERMA')         THEN -m.cantidad
            WHEN m.tipo = 'AJUSTE' THEN 0  -- ver nota abajo
            ELSE 0
        END) AS saldo_kardex_sin_ajustes,
        COUNT(*) FILTER (WHERE m.tipo = 'AJUSTE')  AS total_ajustes,
        MAX(m.created_at) FILTER (WHERE m.tipo = 'AJUSTE') AS ultimo_ajuste_at,
        MAX(m.cantidad)   FILTER (WHERE m.tipo = 'AJUSTE'
            AND m.created_at = (
                SELECT MAX(m2.created_at) FROM movimientos_inventario m2
                WHERE m2.producto_id = m.producto_id
                  AND m2.tenant_id   = m.tenant_id
                  AND m2.tipo        = 'AJUSTE'
            )) AS ultimo_ajuste_valor
    FROM movimientos_inventario m
    WHERE m.tenant_id = :tenantId
    GROUP BY m.producto_id
)
SELECT
    p.id                      AS producto_id,
    p.nombre,
    p.stock_actual            AS stock_producto,
    ks.saldo_kardex_sin_ajustes,
    ks.ultimo_ajuste_valor,
    -- Si hubo ajuste, el saldo efectivo desde ese ajuste puede ser distinto
    (p.stock_actual - ks.saldo_kardex_sin_ajustes) AS diferencia,
    ks.total_ajustes,
    ks.ultimo_ajuste_at
FROM productos p
JOIN kardex_saldo ks ON ks.producto_id = p.id
WHERE p.tenant_id = :tenantId
  AND p.stock_actual <> ks.saldo_kardex_sin_ajustes  -- solo los que difieren
ORDER BY ABS(p.stock_actual - ks.saldo_kardex_sin_ajustes) DESC;


-- ---------------------------------------------------------------------------
-- 2. Kardex completo de un producto con saldo corrido
--    (reemplazar :productoId con el id del producto a auditar)
-- ---------------------------------------------------------------------------
SELECT
    m.id                                             AS movimiento_id,
    m.tipo,
    m.cantidad,
    CASE
        WHEN m.tipo IN ('ENTRADA', 'DEVOLUCION')    THEN  m.cantidad
        WHEN m.tipo IN ('SALIDA',  'MERMA')         THEN -m.cantidad
        WHEN m.tipo = 'AJUSTE'                      THEN  m.cantidad  -- valor absoluto; reinicia saldo
        ELSE 0
    END                                              AS delta,
    SUM(CASE
        WHEN m.tipo IN ('ENTRADA', 'DEVOLUCION')    THEN  m.cantidad
        WHEN m.tipo IN ('SALIDA',  'MERMA')         THEN -m.cantidad
        WHEN m.tipo = 'AJUSTE'                      THEN  0           -- no acumular ajustes
        ELSE 0
    END) OVER (ORDER BY m.created_at ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW)
                                                     AS saldo_corrido,
    m.referencia,
    m.descripcion,
    m.created_at,
    m.sucursal_id,
    u.nombre                                         AS usuario_nombre
FROM movimientos_inventario m
LEFT JOIN usuarios u ON u.id = m.usuario_id
WHERE m.producto_id = :productoId
  AND m.tenant_id   = :tenantId
ORDER BY m.created_at;


-- ---------------------------------------------------------------------------
-- 3. Devoluciones vía DevolucionServiceImpl (flujo Historial)
--    Muestra si reponerStock fue true (el movimiento existe) o false (no existe mov)
-- ---------------------------------------------------------------------------
SELECT
    d.id                        AS devolucion_id,
    d.created_at,
    v.id                        AS venta_id,
    dd.producto_id,
    p.nombre                    AS producto_nombre,
    dd.cantidad_devuelta,
    dd.factor,
    dd.cantidad_devuelta * COALESCE(dd.factor, 1) AS cantidad_base,
    m.id                        AS movimiento_id,
    m.referencia                AS kardex_ref,
    m.created_at                AS movimiento_at,
    CASE WHEN m.id IS NULL THEN 'NO (reponerStock=false)' ELSE 'SI' END AS repuso_stock
FROM devoluciones d
JOIN ventas v              ON v.id = d.venta_id
JOIN devolucion_detalle dd ON dd.devolucion_id = d.id
JOIN productos p           ON p.id = dd.producto_id
LEFT JOIN movimientos_inventario m
    ON m.tipo        = 'DEVOLUCION'
   AND m.referencia  = 'DEV-' || d.id
   AND m.producto_id = dd.producto_id
   AND m.tenant_id   = d.tenant_id
WHERE d.tenant_id = :tenantId
ORDER BY d.created_at DESC
LIMIT 100;


-- ---------------------------------------------------------------------------
-- 4. Devoluciones vía MovimientoInventarioController (flujo Movimientos)
--    Referencia del tipo DEV-NNN creada desde el controller
-- ---------------------------------------------------------------------------
SELECT
    m.id                   AS movimiento_id,
    m.referencia,
    m.cantidad,
    m.lote,
    m.fecha_vencimiento,
    m.created_at,
    m.producto_id,
    p.nombre               AS producto_nombre,
    p.stock_actual         AS stock_producto_actual,
    sl.id                  AS stock_lote_id,
    sl.stock_actual        AS stock_lote_actual,
    sl.lote                AS stock_lote_numero
FROM movimientos_inventario m
JOIN productos p ON p.id = m.producto_id
LEFT JOIN stock_lotes sl ON sl.movimiento_id = m.id
WHERE m.tipo      = 'DEVOLUCION'
  AND m.tenant_id = :tenantId
  AND m.referencia NOT LIKE 'DEV-%'   -- excluye los del flujo Historial
ORDER BY m.created_at DESC
LIMIT 100;


-- ---------------------------------------------------------------------------
-- 5. Verificar stock_lotes vs producto.stock_actual para productos con lotes
--    Los productos farmacia deberían tener SUM(stock_lotes) = producto.stock_actual
-- ---------------------------------------------------------------------------
SELECT
    p.id                              AS producto_id,
    p.nombre,
    p.stock_actual                    AS stock_producto,
    COALESCE(SUM(sl.stock_actual), 0) AS sum_lotes,
    p.stock_actual - COALESCE(SUM(sl.stock_actual), 0) AS diferencia_lotes
FROM productos p
LEFT JOIN stock_lotes sl
    ON sl.producto_id = p.id
   AND sl.tenant_id   = p.tenant_id
WHERE p.tenant_id = :tenantId
GROUP BY p.id, p.nombre, p.stock_actual
HAVING p.stock_actual <> COALESCE(SUM(sl.stock_actual), 0)
   AND EXISTS (SELECT 1 FROM stock_lotes WHERE producto_id = p.id AND tenant_id = p.tenant_id)
ORDER BY ABS(p.stock_actual - COALESCE(SUM(sl.stock_actual), 0)) DESC;


-- ---------------------------------------------------------------------------
-- 6. Historia completa de escrituras sobre producto.stock_actual
--    Inferida de movimientos de inventario ordenados por fecha
--    (para identificar qué operación fue la última antes de la discrepancia)
-- ---------------------------------------------------------------------------
SELECT
    m.id                   AS movimiento_id,
    m.tipo,
    m.cantidad,
    m.referencia,
    m.created_at,
    LAG(m.created_at) OVER (PARTITION BY m.producto_id ORDER BY m.created_at) AS movimiento_anterior_at,
    EXTRACT(EPOCH FROM (m.created_at - LAG(m.created_at) OVER (PARTITION BY m.producto_id ORDER BY m.created_at))) / 60
                           AS minutos_desde_anterior
FROM movimientos_inventario m
WHERE m.producto_id = :productoId
  AND m.tenant_id   = :tenantId
ORDER BY m.created_at DESC
LIMIT 50;


-- ---------------------------------------------------------------------------
-- 7. TREBOL específico: buscar el producto TREBOL y su estado actual
--    (ajustar el ILIKE según el nombre exacto del producto)
-- ---------------------------------------------------------------------------
SELECT
    p.id,
    p.nombre,
    p.stock_actual,
    p.tenant_id,
    (SELECT COUNT(*) FROM movimientos_inventario m WHERE m.producto_id = p.id AND m.tipo = 'DEVOLUCION' AND m.tenant_id = p.tenant_id) AS total_devoluciones,
    (SELECT MAX(created_at) FROM movimientos_inventario m WHERE m.producto_id = p.id AND m.tipo = 'DEVOLUCION' AND m.tenant_id = p.tenant_id) AS ultima_devolucion_at
FROM productos p
WHERE p.nombre ILIKE '%TREBOL%'
  AND p.tenant_id = :tenantId;

-- Kardex TREBOL — reemplazar el subquery por el id real del producto
SELECT * FROM (
    -- (usar query #2 con el id del producto TREBOL)
    SELECT
        m.id, m.tipo, m.cantidad, m.referencia, m.created_at,
        SUM(CASE
            WHEN m.tipo IN ('ENTRADA','DEVOLUCION') THEN m.cantidad
            WHEN m.tipo IN ('SALIDA','MERMA')       THEN -m.cantidad
            ELSE 0
        END) OVER (ORDER BY m.created_at ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS saldo_corrido
    FROM movimientos_inventario m
    WHERE m.producto_id = (SELECT id FROM productos WHERE nombre ILIKE '%TREBOL%' AND tenant_id = :tenantId LIMIT 1)
      AND m.tenant_id = :tenantId
) sub
ORDER BY created_at DESC
LIMIT 20;


-- ---------------------------------------------------------------------------
-- 8. Cetirizina 200mg: verificar múltiples devoluciones
-- ---------------------------------------------------------------------------
SELECT
    p.id, p.nombre, p.stock_actual,
    d.id                   AS devolucion_id,
    d.created_at           AS dev_fecha,
    dd.cantidad_devuelta,
    m.id                   AS mov_id,
    m.referencia,
    m.created_at           AS mov_fecha,
    CASE WHEN m.id IS NULL THEN 'SIN MOVIMIENTO (reponerStock=false?)' ELSE 'OK' END AS estado
FROM productos p
JOIN devolucion_detalle dd ON dd.producto_id = p.id
JOIN devoluciones d        ON d.id = dd.devolucion_id AND d.tenant_id = p.tenant_id
LEFT JOIN movimientos_inventario m
    ON m.tipo = 'DEVOLUCION' AND m.referencia = 'DEV-' || d.id
   AND m.producto_id = p.id AND m.tenant_id = p.tenant_id
WHERE p.nombre ILIKE '%CETIRIZINA%'
  AND p.tenant_id = :tenantId
ORDER BY d.created_at DESC;
