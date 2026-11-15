-- Catálogo central de planes comerciales por segmento y nivel.
-- Fuente de verdad para precios, límites y compatibilidad rubro.
-- Los planes Culqi se configuran en una fase posterior (culqi_plan_id nullable).

CREATE TABLE plan_catalogo (
    codigo              VARCHAR(50)     PRIMARY KEY,
    segmento            VARCHAR(50)     NOT NULL,
    rubro_operativo     VARCHAR(50)     NOT NULL,
    nivel               VARCHAR(10)     NOT NULL,
    precio_mensual      NUMERIC(10, 2)  NOT NULL,
    max_usuarios        INT             NOT NULL,
    max_sucursales      INT             NOT NULL DEFAULT 1,
    max_productos       INT             NOT NULL,
    label_visible       VARCHAR(100),
    culqi_plan_id       VARCHAR(100),
    activo              BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_plan_nivel CHECK (nivel IN ('BASICO', 'PRO')),
    CONSTRAINT ck_plan_max_usuarios   CHECK (max_usuarios   > 0),
    CONSTRAINT ck_plan_max_sucursales CHECK (max_sucursales > 0),
    CONSTRAINT ck_plan_max_productos  CHECK (max_productos  > 0),
    CONSTRAINT ck_plan_precio         CHECK (precio_mensual > 0)
);

-- Seed inicial de planes comerciales aprobados.
-- Reproducible: ON CONFLICT DO NOTHING evita duplicados en re-ejecuciones.
INSERT INTO plan_catalogo (codigo, segmento, rubro_operativo, nivel, precio_mensual, max_usuarios, max_sucursales, max_productos, label_visible) VALUES

-- Farmacias y boticas (dos rubros operativos comparten el mismo segmento comercial)
('BOTICA_BASICO',            'FARMACIA_BOTICA',    'BOTICA',            'BASICO',  89.00,  5, 1, 2000, 'Plan Básico — Botica'),
('BOTICA_PRO',               'FARMACIA_BOTICA',    'BOTICA',            'PRO',    169.00, 15, 5, 5000, 'Plan Pro — Botica'),
('FARMACIA_BASICO',          'FARMACIA_BOTICA',    'FARMACIA',          'BASICO',  89.00,  5, 1, 2000, 'Plan Básico — Farmacia'),
('FARMACIA_PRO',             'FARMACIA_BOTICA',    'FARMACIA',          'PRO',    169.00, 15, 5, 5000, 'Plan Pro — Farmacia'),

-- Veterinaria (precio diferenciado; max_usuarios BASICO = 10)
('VETERINARIA_BASICO',       'VETERINARIA',        'VETERINARIA',       'BASICO', 109.00, 10, 1, 2000, 'Plan Básico — Veterinaria'),
('VETERINARIA_PRO',          'VETERINARIA',        'VETERINARIA',       'PRO',    189.00, 15, 5, 5000, 'Plan Pro — Veterinaria'),

-- Tiendas de ropa (rubro operativo propio para conservar módulo de variantes existente)
('TIENDA_ROPA_BASICO',       'TIENDA_ROPA',        'TIENDA_ROPA',       'BASICO',  89.00,  5, 1, 2000, 'Plan Básico — Tienda de Ropa'),
('TIENDA_ROPA_PRO',          'TIENDA_ROPA',        'TIENDA_ROPA',       'PRO',    169.00, 15, 5, 5000, 'Plan Pro — Tienda de Ropa'),

-- Tiendas y bodegas (precio distinto al de tienda de ropa)
('TIENDA_BODEGA_BASICO',     'TIENDA_BODEGA',      'TIENDA',            'BASICO',  79.00,  5, 1, 2000, 'Plan Básico — Tienda y Bodega'),
('TIENDA_BODEGA_PRO',        'TIENDA_BODEGA',      'TIENDA',            'PRO',    149.00, 15, 5, 5000, 'Plan Pro — Tienda y Bodega'),

-- Ferreterías
('FERRETERIA_BASICO',        'FERRETERIA',         'FERRETERIA',        'BASICO',  99.00,  5, 1, 2000, 'Plan Básico — Ferretería'),
('FERRETERIA_PRO',           'FERRETERIA',         'FERRETERIA',        'PRO',    179.00, 15, 5, 5000, 'Plan Pro — Ferretería'),

-- Minimarkets
('MINIMARKET_BASICO',        'MINIMARKET',         'MINIMARKET',        'BASICO',  99.00,  5, 1, 2000, 'Plan Básico — Minimarket'),
('MINIMARKET_PRO',           'MINIMARKET',         'MINIMARKET',        'PRO',    179.00, 15, 5, 5000, 'Plan Pro — Minimarket'),

-- Almacenes (sin rubro operativo propio aún; usa OTRO hasta migración posterior)
('ALMACEN_BASICO',           'ALMACENES',          'OTRO',              'BASICO',  89.00,  5, 1, 2000, 'Plan Básico — Almacén'),
('ALMACEN_PRO',              'ALMACENES',          'OTRO',              'PRO',    169.00, 15, 5, 5000, 'Plan Pro — Almacén'),

-- Restaurantes
('RESTAURANTE_BASICO',       'RESTAURANTE',        'RESTAURANTE',       'BASICO', 119.00,  5, 1, 2000, 'Plan Básico — Restaurante'),
('RESTAURANTE_PRO',          'RESTAURANTE',        'RESTAURANTE',       'PRO',    199.00, 15, 5, 5000, 'Plan Pro — Restaurante'),

-- Empresas de servicios
('EMPRESA_SERVICIOS_BASICO', 'EMPRESA_SERVICIOS',  'EMPRESA_SERVICIOS', 'BASICO',  99.00,  5, 1, 2000, 'Plan Básico — Empresa de Servicios'),
('EMPRESA_SERVICIOS_PRO',    'EMPRESA_SERVICIOS',  'EMPRESA_SERVICIOS', 'PRO',    179.00, 15, 5, 5000, 'Plan Pro — Empresa de Servicios'),

-- Otros negocios (plan genérico de fallback)
('OTRO_BASICO',              'OTRO',               'OTRO',              'BASICO',  89.00,  5, 1, 2000, 'Plan Básico'),
('OTRO_PRO',                 'OTRO',               'OTRO',              'PRO',    169.00, 15, 5, 5000, 'Plan Pro')

ON CONFLICT (codigo) DO NOTHING;
