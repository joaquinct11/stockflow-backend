-- Habilita el módulo pg_trgm para soportar ILIKE con índice trigrama
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Índice trigrama en nom_prod para búsquedas ILIKE '%texto%' rápidas
CREATE INDEX IF NOT EXISTS idx_catalogo_digemid_nom_prod_trgm
    ON catalogo_digemid USING gin (nom_prod gin_trgm_ops);

-- Índice trigrama en nom_ifa
CREATE INDEX IF NOT EXISTS idx_catalogo_digemid_nom_ifa_trgm
    ON catalogo_digemid USING gin (nom_ifa gin_trgm_ops);
