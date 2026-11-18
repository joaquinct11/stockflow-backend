-- V138: Items (productos) de cada guía de remisión.
CREATE TABLE guia_remision_items (
    id                  BIGSERIAL PRIMARY KEY,
    guia_remision_id    BIGINT       NOT NULL REFERENCES guias_remision(id) ON DELETE CASCADE,
    producto_id         BIGINT       NOT NULL REFERENCES productos(id),
    descripcion         VARCHAR(500) NOT NULL,
    cantidad            NUMERIC(12,3) NOT NULL,
    unidad_medida       VARCHAR(10)  NOT NULL DEFAULT 'NIU',  -- Catálogo 03 SUNAT
    codigo_producto     VARCHAR(100),  -- código interno / código de barras
    tenant_id           VARCHAR(100) NOT NULL
);

CREATE INDEX idx_gri_guia ON guia_remision_items(guia_remision_id);
CREATE INDEX idx_gri_producto ON guia_remision_items(producto_id);
