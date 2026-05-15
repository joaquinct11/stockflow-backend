-- ============================================================
-- V36 — Limpieza de columnas redundantes y adición de índices
-- ============================================================

-- ── 1. productos.categoria (VARCHAR) — reemplazada por categoria_id FK en V32
-- ──────────────────────────────────────────────────────────────────────────────
ALTER TABLE productos DROP COLUMN IF EXISTS categoria;

-- ── 2. usuarios.fecha_creacion — duplicado de created_at
-- ──────────────────────────────────────────────────────────────────────────────
ALTER TABLE usuarios DROP COLUMN IF EXISTS fecha_creacion;

-- ── 3. devoluciones.fecha_devolucion — duplicado de created_at
-- ──────────────────────────────────────────────────────────────────────────────
ALTER TABLE devoluciones DROP COLUMN IF EXISTS fecha_devolucion;

-- ── 4. productos.deleted_at — nunca se escribe (soft-delete usa activo=false)
-- ──────────────────────────────────────────────────────────────────────────────
ALTER TABLE productos DROP COLUMN IF EXISTS deleted_at;

-- ── 5. suscripciones.intentos_fallidos — declarado, nunca incrementado
-- ──────────────────────────────────────────────────────────────────────────────
ALTER TABLE suscripciones DROP COLUMN IF EXISTS intentos_fallidos;

-- ── 6. FK faltante en notas_credito.venta_uso_id
-- ──────────────────────────────────────────────────────────────────────────────
ALTER TABLE notas_credito
    ADD CONSTRAINT fk_nc_venta_uso
    FOREIGN KEY (venta_uso_id) REFERENCES ventas(id)
    ON DELETE SET NULL;

-- ── 7. Índices faltantes en devolucion_detalle (tabla sin ningún índice)
-- ──────────────────────────────────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_dev_detalle_devolucion ON devolucion_detalle(devolucion_id);
CREATE INDEX IF NOT EXISTS idx_dev_detalle_producto   ON devolucion_detalle(producto_id);

-- ── 8. Índices faltantes en devoluciones
-- ──────────────────────────────────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_devoluciones_usuario ON devoluciones(usuario_id);
CREATE INDEX IF NOT EXISTS idx_devoluciones_estado  ON devoluciones(estado);

-- ── 9. Índices faltantes en notas_credito
-- ──────────────────────────────────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_nc_devolucion ON notas_credito(devolucion_id);
CREATE INDEX IF NOT EXISTS idx_nc_estado     ON notas_credito(estado);
CREATE INDEX IF NOT EXISTS idx_nc_venta_uso  ON notas_credito(venta_uso_id);

-- ── 10. Índices faltantes en comprobantes
-- ──────────────────────────────────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_comprobantes_venta  ON comprobantes(venta_id);
CREATE INDEX IF NOT EXISTS idx_comprobantes_estado ON comprobantes(estado);

-- ── 11. Índices faltantes en categorias y unidad_medida
-- ──────────────────────────────────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_categorias_tenant    ON categorias(tenant_id);
CREATE INDEX IF NOT EXISTS idx_unidad_medida_tenant ON unidad_medida(tenant_id);

-- ── 12. Índices faltantes en orden_compra y recepcion
-- ──────────────────────────────────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_oc_usuario_creador  ON orden_compra(usuario_creador_id);
CREATE INDEX IF NOT EXISTS idx_recepcion_receptor  ON recepcion(usuario_receptor_id);
