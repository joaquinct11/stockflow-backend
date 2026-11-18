-- V141: Permisos para el módulo de Guías de Remisión Electrónicas (GRE).
-- Los permisos se insertan sin rol_id (permiso libre).
-- La asignación a roles PRO se hace a través de RolePermissionDefaults (en código).

INSERT INTO permisos (nombre, descripcion, created_at)
VALUES
    ('VER_GRE',                 'Ver listado y detalle de guías de remisión',              NOW()),
    ('EMITIR_GRE',              'Crear y emitir guías de remisión electrónicas',           NOW()),
    ('CONFIRMAR_RECEPCION_GRE', 'Confirmar la recepción de mercadería en sucursal destino', NOW()),
    ('CANCELAR_GRE_BORRADOR',   'Cancelar borradores de guías aún no emitidas a SUNAT',    NOW())
ON CONFLICT (nombre) DO NOTHING;
