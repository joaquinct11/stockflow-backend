-- Assign VER_MIS_VENTAS as a base (default) permission for the VENDEDOR role.
-- This ensures that any VENDEDOR can view their own sales without needing
-- an explicit usuario_permisos row.
UPDATE permisos
SET rol_id = (SELECT id FROM roles WHERE nombre = 'VENDEDOR')
WHERE nombre = 'VER_MIS_VENTAS'
  AND rol_id IS NULL;

-- Assign CREAR_INVENTARIO as a base permission for GESTOR_INVENTARIO so that
-- inventory managers can log stock movements by default.
UPDATE permisos
SET rol_id = (SELECT id FROM roles WHERE nombre = 'GESTOR_INVENTARIO')
WHERE nombre = 'CREAR_INVENTARIO'
  AND rol_id IS NULL;
