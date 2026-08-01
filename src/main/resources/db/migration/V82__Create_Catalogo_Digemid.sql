CREATE TABLE IF NOT EXISTS catalogo_digemid (
    id              BIGSERIAL PRIMARY KEY,
    cod_prod        VARCHAR(20)  NOT NULL,
    nom_prod        VARCHAR(500) NOT NULL,
    concent         VARCHAR(200),
    nom_form_farm   VARCHAR(200),
    presentac       VARCHAR(500),
    fraccion        NUMERIC(10,2),
    num_reg_san     VARCHAR(50),
    nom_titular     VARCHAR(500),
    nom_fabricante  VARCHAR(500),
    nom_ifa         VARCHAR(500),
    nom_rubro       VARCHAR(200),
    situacion       VARCHAR(10)
);

CREATE INDEX IF NOT EXISTS idx_catalogo_digemid_cod_prod    ON catalogo_digemid(cod_prod);
CREATE INDEX IF NOT EXISTS idx_catalogo_digemid_num_reg_san ON catalogo_digemid(num_reg_san);
CREATE INDEX IF NOT EXISTS idx_catalogo_digemid_nom_prod    ON catalogo_digemid USING gin(to_tsvector('spanish', nom_prod));
