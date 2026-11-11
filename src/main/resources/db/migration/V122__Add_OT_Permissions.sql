INSERT INTO permisos (nombre, descripcion, created_at) VALUES
  ('VER_OT',             'Listar y consultar órdenes de trabajo',             NOW()),
  ('CREAR_OT',           'Registrar nuevas órdenes de trabajo',               NOW()),
  ('EDITAR_OT',          'Modificar datos básicos de una orden de trabajo',   NOW()),
  ('ASIGNAR_TECNICO_OT', 'Asignar o cambiar el técnico en una OT',            NOW()),
  ('CAMBIAR_ESTADO_OT',  'Cambiar el estado de una orden de trabajo',         NOW());
