-- V134: Rol TECNICO_SERVICIO para operadores de Órdenes de Trabajo en EMPRESA_SERVICIOS.
-- Accede a OTs, equipos de clientes y productos; sin acceso a caja, facturación ni administración.

INSERT INTO roles (nombre, descripcion, created_at)
VALUES ('TECNICO_SERVICIO', 'Técnico de servicio — opera órdenes de trabajo y consume materiales', NOW())
ON CONFLICT (nombre) DO NOTHING;
