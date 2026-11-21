-- V147: Catálogo oficial de ubigeos SUNAT/INEI (departamento → provincia → distrito).
-- Usado para validar ubigeos en Guías de Remisión antes de llamar a Lucode.
CREATE TABLE IF NOT EXISTS catalogo_ubigeos (
    codigo       VARCHAR(6)   NOT NULL PRIMARY KEY,
    departamento VARCHAR(100) NOT NULL,
    provincia    VARCHAR(100) NOT NULL,
    distrito     VARCHAR(100) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_ubigeos_departamento ON catalogo_ubigeos (departamento);
CREATE INDEX IF NOT EXISTS idx_ubigeos_dep_prov     ON catalogo_ubigeos (departamento, provincia);
