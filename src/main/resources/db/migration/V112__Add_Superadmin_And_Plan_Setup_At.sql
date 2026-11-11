-- Rol de administrador de plataforma (distinto del ADMIN de tenant).
-- Solo los operadores de Fluxus deben tener este rol.
-- No se asigna durante el registro: se otorga manualmente por consola o script de ops.
INSERT INTO roles (nombre, descripcion, created_at)
VALUES ('SUPERADMIN', 'Administrador de plataforma Fluxus', NOW())
ON CONFLICT (nombre) DO NOTHING;

-- Registra cuándo se configuró cada plan en Culqi (para auditoría de setup).
ALTER TABLE plan_catalogo
    ADD COLUMN IF NOT EXISTS culqi_setup_at TIMESTAMP;

COMMENT ON COLUMN plan_catalogo.culqi_setup_at IS
    'Momento en que culqi_plan_id fue asignado por el endpoint de setup. NULL si aún no configurado.';
