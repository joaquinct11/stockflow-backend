-- V135: Adds VER_TODAS_OT permission for ADMIN/GERENTE visibility over all tenant OTs.
-- TECNICO_SERVICIO retains only VER_OT, which is now scoped to assigned OTs in the service layer.
INSERT INTO permisos (nombre, descripcion, created_at)
VALUES ('VER_TODAS_OT', 'Ver todas las órdenes de trabajo del tenant (no solo las asignadas)', NOW());
