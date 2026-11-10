-- Permisos para devoluciones y clasificaciones en órdenes de trabajo (Fase 2B)
INSERT INTO permisos (nombre, descripcion, created_at)
VALUES
    ('DEVOLVER_CONSUMO_OT',
     'Registrar devolución de material consumido en orden de trabajo',
     NOW()),
    ('CLASIFICAR_CONSUMO_OT',
     'Clasificar material consumido en orden de trabajo (INSTALADO/RECUPERABLE/DAÑADO/PERDIDO)',
     NOW())
ON CONFLICT (nombre) DO NOTHING;
