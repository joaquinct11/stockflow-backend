-- V13: Actualizar permisos para coherencia con RBAC

-- Eliminar permisos existentes para recrearlos de forma coherente
DELETE FROM permisos;

-- Permisos del rol ADMIN (id=1): acceso completo
INSERT INTO permisos (nombre, descripcion, rol_id, created_at) VALUES
    ('GESTIONAR_ROLES',          'Crear, editar y eliminar roles',                      1, NOW()),
    ('GESTIONAR_PERMISOS',       'Crear, editar y eliminar permisos',                   1, NOW()),
    ('GESTIONAR_USUARIOS',       'Crear, editar, activar/desactivar y eliminar usuarios', 1, NOW()),
    ('GESTIONAR_SUSCRIPCIONES',  'Crear, activar y cancelar suscripciones',             1, NOW()),
    ('GESTIONAR_PRODUCTOS',      'Crear, editar y eliminar productos',                  1, NOW()),
    ('VER_PRODUCTOS',            'Consultar productos e inventario',                    1, NOW()),
    ('GESTIONAR_PROVEEDORES',    'Crear, editar y eliminar proveedores',                1, NOW()),
    ('VER_PROVEEDORES',          'Consultar proveedores',                               1, NOW()),
    ('GESTIONAR_INVENTARIO',     'Registrar y eliminar movimientos de inventario',      1, NOW()),
    ('VER_INVENTARIO',           'Consultar movimientos de inventario',                 1, NOW()),
    ('GESTIONAR_VENTAS',         'Crear y eliminar ventas',                             1, NOW()),
    ('VER_VENTAS',               'Consultar ventas',                                    1, NOW())
ON CONFLICT (nombre) DO NOTHING;

-- Permisos del rol GERENTE (id=4): gestión de personal y lectura general
INSERT INTO permisos (nombre, descripcion, rol_id, created_at) VALUES
    ('GERENTE_GESTIONAR_USUARIOS',      'Crear, editar y activar/desactivar usuarios',     4, NOW()),
    ('GERENTE_GESTIONAR_SUSCRIPCIONES', 'Activar y cancelar suscripciones',                4, NOW()),
    ('GERENTE_VER_PRODUCTOS',           'Consultar productos e inventario',                4, NOW()),
    ('GERENTE_VER_PROVEEDORES',         'Consultar proveedores',                           4, NOW()),
    ('GERENTE_VER_INVENTARIO',          'Consultar movimientos de inventario',             4, NOW()),
    ('GERENTE_VER_VENTAS',              'Consultar ventas y reportes',                     4, NOW())
ON CONFLICT (nombre) DO NOTHING;

-- Permisos del rol GESTOR_INVENTARIO (id=3): gestión de stock y abastecimiento
INSERT INTO permisos (nombre, descripcion, rol_id, created_at) VALUES
    ('GESTOR_GESTIONAR_PRODUCTOS',   'Crear, editar y eliminar productos',           3, NOW()),
    ('GESTOR_VER_PRODUCTOS',         'Consultar productos e inventario',             3, NOW()),
    ('GESTOR_GESTIONAR_PROVEEDORES', 'Crear, editar y eliminar proveedores',         3, NOW()),
    ('GESTOR_VER_PROVEEDORES',       'Consultar proveedores',                        3, NOW()),
    ('GESTOR_GESTIONAR_INVENTARIO',  'Registrar movimientos de inventario',          3, NOW()),
    ('GESTOR_VER_INVENTARIO',        'Consultar movimientos de inventario',          3, NOW())
ON CONFLICT (nombre) DO NOTHING;

-- Permisos del rol VENDEDOR (id=2): registro y consulta de ventas
INSERT INTO permisos (nombre, descripcion, rol_id, created_at) VALUES
    ('VENDEDOR_VER_PRODUCTOS',   'Consultar productos disponibles para la venta', 2, NOW()),
    ('VENDEDOR_VER_PROVEEDORES', 'Consultar proveedores',                         2, NOW()),
    ('VENDEDOR_CREAR_VENTA',     'Registrar nuevas ventas',                       2, NOW()),
    ('VENDEDOR_VER_VENTAS',      'Consultar ventas propias',                      2, NOW())
ON CONFLICT (nombre) DO NOTHING;
