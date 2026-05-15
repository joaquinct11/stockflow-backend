-- La columna fecha_devolucion fue añadida al entity Devolucion pero no
-- existía en la tabla creada originalmente en UAT (V34 fue modificado
-- localmente después de haber sido aplicado). Este migration la agrega
-- de forma idempotente para no romper entornos que ya la tienen.

ALTER TABLE devoluciones
    ADD COLUMN IF NOT EXISTS fecha_devolucion TIMESTAMP;

-- Rellenar filas existentes con created_at para mantener consistencia
UPDATE devoluciones
SET fecha_devolucion = created_at
WHERE fecha_devolucion IS NULL;
