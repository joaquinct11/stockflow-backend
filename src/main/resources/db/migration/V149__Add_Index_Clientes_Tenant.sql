-- F-07: Auditoría de índices de clientes.
-- V40 ya creó todos los índices necesarios:
--   idx_clientes_tenant          ON clientes(tenant_id)
--   idx_clientes_tenant_activo   ON clientes(tenant_id, activo)
--   idx_clientes_nombre          ON clientes(tenant_id, LOWER(nombre))  ← índice funcional
--   idx_clientes_documento       ON clientes(tenant_id, numero_documento)
--
-- La búsqueda por nombre usa LOWER(nombre) LIKE '%texto%'. El índice funcional
-- idx_clientes_nombre ayuda con el filtro tenant_id pero no con LIKE '%texto%'
-- (patrón con % al inicio). Para búsqueda full-text se requeriría pg_trgm + GIN.
--
-- Esta migración es un no-op seguro: los IF NOT EXISTS son ignorados porque
-- los índices ya existen.
CREATE INDEX IF NOT EXISTS idx_clientes_tenant_activo
    ON clientes (tenant_id, activo);
