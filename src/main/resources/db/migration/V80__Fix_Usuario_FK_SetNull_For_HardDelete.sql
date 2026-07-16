-- Permite borrar un usuario de la tabla usuarios sin perder ventas,
-- movimientos, cajas, devoluciones, recepcion ni retiros.
-- Las columnas que antes eran NOT NULL + CASCADE pasan a nullable + SET NULL.

-- ── ventas ────────────────────────────────────────────────────────────────────
ALTER TABLE ventas DROP CONSTRAINT IF EXISTS ventas_vendedor_id_fkey;
ALTER TABLE ventas ALTER COLUMN vendedor_id DROP NOT NULL;
ALTER TABLE ventas
    ADD CONSTRAINT ventas_vendedor_id_fkey
    FOREIGN KEY (vendedor_id) REFERENCES usuarios(id) ON DELETE SET NULL;

-- ── movimientos_inventario ────────────────────────────────────────────────────
ALTER TABLE movimientos_inventario DROP CONSTRAINT IF EXISTS movimientos_inventario_usuario_id_fkey;
ALTER TABLE movimientos_inventario ALTER COLUMN usuario_id DROP NOT NULL;
ALTER TABLE movimientos_inventario
    ADD CONSTRAINT movimientos_inventario_usuario_id_fkey
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE SET NULL;

-- ── cajas ─────────────────────────────────────────────────────────────────────
ALTER TABLE cajas DROP CONSTRAINT IF EXISTS cajas_usuario_id_fkey;
ALTER TABLE cajas ALTER COLUMN usuario_id DROP NOT NULL;
ALTER TABLE cajas
    ADD CONSTRAINT cajas_usuario_id_fkey
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE SET NULL;

-- ── devoluciones ──────────────────────────────────────────────────────────────
ALTER TABLE devoluciones DROP CONSTRAINT IF EXISTS devoluciones_usuario_id_fkey;
ALTER TABLE devoluciones ALTER COLUMN usuario_id DROP NOT NULL;
ALTER TABLE devoluciones
    ADD CONSTRAINT devoluciones_usuario_id_fkey
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE SET NULL;

-- ── retiros_caja ──────────────────────────────────────────────────────────────
ALTER TABLE retiros_caja DROP CONSTRAINT IF EXISTS retiros_caja_usuario_id_fkey;
ALTER TABLE retiros_caja ALTER COLUMN usuario_id DROP NOT NULL;
ALTER TABLE retiros_caja
    ADD CONSTRAINT retiros_caja_usuario_id_fkey
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE SET NULL;

-- ── orden_compra ─────────────────────────────────────────────────────────────
ALTER TABLE orden_compra DROP CONSTRAINT IF EXISTS orden_compra_usuario_creador_id_fkey;
ALTER TABLE orden_compra ALTER COLUMN usuario_creador_id DROP NOT NULL;
ALTER TABLE orden_compra
    ADD CONSTRAINT orden_compra_usuario_creador_id_fkey
    FOREIGN KEY (usuario_creador_id) REFERENCES usuarios(id) ON DELETE SET NULL;

-- ── recepcion ────────────────────────────────────────────────────────────────
-- recepcion solo tiene usuario_receptor_id (no usuario_creador_id)
ALTER TABLE recepcion DROP CONSTRAINT IF EXISTS recepcion_usuario_receptor_id_fkey;
ALTER TABLE recepcion ALTER COLUMN usuario_receptor_id DROP NOT NULL;
ALTER TABLE recepcion
    ADD CONSTRAINT recepcion_usuario_receptor_id_fkey
    FOREIGN KEY (usuario_receptor_id) REFERENCES usuarios(id) ON DELETE SET NULL;

-- NOTA: usuario_permisos y refresh_tokens conservan ON DELETE CASCADE
-- (se eliminan automáticamente al borrar el usuario, es el comportamiento correcto).
