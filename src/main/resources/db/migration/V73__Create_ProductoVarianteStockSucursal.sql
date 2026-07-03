-- ============================================================
-- V73: Stock de variantes por sucursal (Plan PRO)
-- ============================================================
-- Espejo de producto_stock_sucursal pero para variantes.
-- Plan BÁSICO: la tabla queda vacía; se sigue usando
--   producto_variantes.stock_actual como total global.
-- Plan PRO: cada movimiento con sucursalId actualiza esta
--   tabla, y producto_variantes.stock_actual se mantiene
--   como suma de todos los locales (total agregado).
-- ============================================================

CREATE TABLE IF NOT EXISTS producto_variante_stock_sucursal (
    id          BIGSERIAL       PRIMARY KEY,
    variante_id BIGINT          NOT NULL REFERENCES producto_variantes(id)  ON DELETE CASCADE,
    sucursal_id BIGINT          NOT NULL REFERENCES sucursales(id)           ON DELETE CASCADE,
    tenant_id   VARCHAR(100)    NOT NULL,
    stock_actual INTEGER         NOT NULL DEFAULT 0,
    stock_minimo INTEGER         NOT NULL DEFAULT 0,
    CONSTRAINT uq_variante_sucursal UNIQUE (variante_id, sucursal_id)
);

CREATE INDEX IF NOT EXISTS idx_pvss_variante ON producto_variante_stock_sucursal(variante_id);
CREATE INDEX IF NOT EXISTS idx_pvss_sucursal ON producto_variante_stock_sucursal(sucursal_id);
CREATE INDEX IF NOT EXISTS idx_pvss_tenant   ON producto_variante_stock_sucursal(tenant_id);

-- ── Backfill: migrar stock actual de variantes a la sucursal principal ────────
-- Solo aplica a tenants que ya tienen al menos una sucursal (es decir, ya están en PRO
-- o acaban de hacer el upgrade). Para Plan BÁSICO sin sucursales no inserta nada.
INSERT INTO producto_variante_stock_sucursal (variante_id, sucursal_id, tenant_id, stock_actual, stock_minimo)
SELECT
    pv.id          AS variante_id,
    s.id           AS sucursal_id,
    pv.tenant_id,
    pv.stock_actual,
    pv.stock_minimo
FROM producto_variantes pv
INNER JOIN sucursales s
       ON  s.tenant_id   = pv.tenant_id
       AND s.es_principal = true
WHERE pv.activo = true
ON CONFLICT (variante_id, sucursal_id) DO NOTHING;
