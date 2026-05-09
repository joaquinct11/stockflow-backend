-- V31: Campos de configuración del negocio en la tabla tenants

ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS ruc              VARCHAR(20),
    ADD COLUMN IF NOT EXISTS direccion        VARCHAR(255),
    ADD COLUMN IF NOT EXISTS telefono         VARCHAR(30),
    ADD COLUMN IF NOT EXISTS email_contacto   VARCHAR(120),
    ADD COLUMN IF NOT EXISTS ciudad           VARCHAR(100),
    ADD COLUMN IF NOT EXISTS logo_base64      TEXT,
    ADD COLUMN IF NOT EXISTS moneda           VARCHAR(10)       DEFAULT 'S/.',
    ADD COLUMN IF NOT EXISTS igv_porcentaje   DOUBLE PRECISION  DEFAULT 18.0,
    ADD COLUMN IF NOT EXISTS pie_pagina_pdf   VARCHAR(255),
    ADD COLUMN IF NOT EXISTS serie_boleta     VARCHAR(10)       DEFAULT 'B001',
    ADD COLUMN IF NOT EXISTS serie_factura    VARCHAR(10)       DEFAULT 'F001';
