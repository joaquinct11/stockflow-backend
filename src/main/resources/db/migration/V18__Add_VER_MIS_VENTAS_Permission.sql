-- Nuevo permiso para que un vendedor pueda ver únicamente sus propias ventas
INSERT INTO permisos (nombre, descripcion, created_at)
VALUES ('VER_MIS_VENTAS', 'Permiso para ver sus propias ventas', NOW())
ON CONFLICT (nombre) DO NOTHING;
