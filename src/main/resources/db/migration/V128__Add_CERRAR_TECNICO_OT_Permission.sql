-- Fase 2C: permiso para el cierre técnico de órdenes de trabajo
INSERT INTO permisos (nombre, descripcion, created_at)
VALUES ('CERRAR_TECNICO_OT',
        'Realizar el cierre técnico de una orden de trabajo (diagnóstico final, solución aplicada y transición a CERRADA_TECN)',
        NOW())
ON CONFLICT (nombre) DO NOTHING;
