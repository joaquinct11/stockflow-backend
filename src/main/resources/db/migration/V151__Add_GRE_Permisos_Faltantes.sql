-- F-19: Insertar los permisos GRE que faltaban en la tabla permisos.
-- V141 solo insertó VER_GRE, EMITIR_GRE, CONFIRMAR_RECEPCION_GRE y CANCELAR_GRE_BORRADOR.
-- EDITAR_GRE_BORRADOR y ELIMINAR_GRE_BORRADOR existían en RolePermissionDefaults y en las
-- anotaciones @PreAuthorize del controller pero no en la tabla, lo que impedía asignarlos
-- como permisos individuales desde el panel de administración.
INSERT INTO permisos (nombre, descripcion)
VALUES
    ('EDITAR_GRE_BORRADOR',   'Modificar guías en estado BORRADOR o ERROR antes de emitir'),
    ('ELIMINAR_GRE_BORRADOR', 'Eliminar guías BORRADOR o ERROR sin movimientos de inventario')
ON CONFLICT (nombre) DO NOTHING;
