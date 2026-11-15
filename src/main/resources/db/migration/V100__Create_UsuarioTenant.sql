-- Relación M:N entre usuarios y tenants, con rol contextual por tenant.
-- El rol se almacena por tenant para que un mismo usuario pueda tener
-- diferentes roles en diferentes tenants (ADMIN en A, CAJERO en B).
CREATE TABLE IF NOT EXISTS usuario_tenant (
    id          BIGSERIAL    PRIMARY KEY,
    usuario_id  BIGINT       NOT NULL REFERENCES usuarios(id)       ON DELETE CASCADE,
    tenant_id   VARCHAR(100) NOT NULL REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    rol_id      BIGINT       NOT NULL REFERENCES roles(id),
    activo      BOOLEAN      NOT NULL DEFAULT true,
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uq_usuario_tenant UNIQUE (usuario_id, tenant_id)
);

CREATE INDEX IF NOT EXISTS idx_ut_usuario ON usuario_tenant(usuario_id);
CREATE INDEX IF NOT EXISTS idx_ut_tenant  ON usuario_tenant(tenant_id);

-- Poblar desde la relación existente usuarios.tenant_id + usuarios.rol_id.
-- usuarios.tenant_id y usuarios.rol_id se mantienen sin modificar (backward compat).
INSERT INTO usuario_tenant (usuario_id, tenant_id, rol_id)
SELECT u.id, u.tenant_id, u.rol_id
FROM   usuarios u
WHERE  u.tenant_id IS NOT NULL
ON CONFLICT (usuario_id, tenant_id) DO NOTHING;
