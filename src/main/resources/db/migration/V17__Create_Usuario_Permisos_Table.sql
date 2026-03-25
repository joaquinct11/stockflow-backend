-- Tabla de asignación directa de permisos a usuarios (por tenant)
CREATE TABLE usuario_permisos (
    id          BIGSERIAL PRIMARY KEY,
    usuario_id  BIGINT       NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    permiso_id  BIGINT       NOT NULL REFERENCES permisos(id) ON DELETE CASCADE,
    tenant_id   VARCHAR(100) NOT NULL REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_usuario_permiso_tenant UNIQUE (usuario_id, permiso_id, tenant_id)
);

CREATE INDEX idx_usuario_permisos_usuario_id ON usuario_permisos(usuario_id);
CREATE INDEX idx_usuario_permisos_permiso_id ON usuario_permisos(permiso_id);
CREATE INDEX idx_usuario_permisos_tenant_id  ON usuario_permisos(tenant_id);

-- Añadir permisos adicionales para todos los módulos
INSERT INTO permisos (nombre, descripcion, created_at) VALUES
    ('CREAR_INVENTARIO',    'Permiso para crear movimientos de inventario', NOW()),
    ('ELIMINAR_INVENTARIO', 'Permiso para eliminar movimientos de inventario', NOW()),
    ('ELIMINAR_VENTA',      'Permiso para eliminar ventas', NOW()),
    ('CREAR_PROVEEDOR',     'Permiso para crear proveedores', NOW()),
    ('EDITAR_PROVEEDOR',    'Permiso para editar proveedores', NOW()),
    ('ELIMINAR_PROVEEDOR',  'Permiso para eliminar proveedores', NOW()),
    ('VER_SUSCRIPCIONES',   'Permiso para ver suscripciones', NOW())
ON CONFLICT (nombre) DO NOTHING;
