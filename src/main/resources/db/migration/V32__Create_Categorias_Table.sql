CREATE TABLE categorias (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    tenant_id VARCHAR(100) NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Defaults globales (tenant_id = NULL → todos los tenants los ven)
INSERT INTO categorias (nombre, tenant_id) VALUES
('Alimentos', NULL),
('Bebidas', NULL),
('Limpieza e higiene', NULL),
('Cuidado personal', NULL),
('Electrónica', NULL),
('Ropa y calzado', NULL),
('Herramientas', NULL),
('Medicamentos', NULL),
('Papelería', NULL),
('Otros', NULL);

-- Agregar FK categoria_id a productos (nullable para no romper datos existentes)
ALTER TABLE productos ADD COLUMN IF NOT EXISTS categoria_id BIGINT NULL REFERENCES categorias(id);
