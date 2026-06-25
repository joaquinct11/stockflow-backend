CREATE TABLE tipos_certificado (
    id     SERIAL PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL UNIQUE,
    activo BOOLEAN      NOT NULL DEFAULT TRUE
);

INSERT INTO tipos_certificado (nombre) VALUES
    ('Carnet de Sanidad'),
    ('Fumigación'),
    ('Extintor'),
    ('Instalación Eléctrica'),
    ('Licencia de Funcionamiento'),
    ('Certificado INDECI'),
    ('Calibración de Balanza');
