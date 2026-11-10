INSERT INTO roles (nombre, descripcion, created_at) VALUES
    ('VETERINARIO',          'Veterinario — acceso clínico completo',        NOW()),
    ('RECEPCIONISTA',        'Recepcionista — agenda y atención al cliente',  NOW()),
    ('AUXILIAR_VETERINARIO', 'Auxiliar veterinario — soporte clínico',       NOW()),
    ('GROOMER',              'Groomer — peluquería y estética animal',       NOW())
ON CONFLICT (nombre) DO NOTHING;
