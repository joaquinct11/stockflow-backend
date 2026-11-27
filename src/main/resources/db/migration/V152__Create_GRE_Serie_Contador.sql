-- V152: Contador atómico de correlativos por (tenant_id, serie) para Guías de Remisión.
--
-- Reemplaza el patrón MAX(correlativo)+1 (que reciclaba números al eliminar GREs)
-- por un contador de solo-avance con bloqueo pesimista, garantizando que un correlativo
-- enviado a Lucode nunca sea reutilizado aunque la GRE local sea eliminada.
--
-- Inicialización: se carga el MAX(correlativo) existente para no generar colisiones
-- con GREs ya existentes en la tabla guias_remision.

CREATE TABLE gre_serie_contador (
    tenant_id VARCHAR(100) NOT NULL REFERENCES tenants(tenant_id),
    serie     VARCHAR(10)  NOT NULL,
    ultimo    INTEGER      NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id, serie)
);

-- Inicializar desde los correlativos actuales (si ya existen GREs)
INSERT INTO gre_serie_contador (tenant_id, serie, ultimo)
SELECT tenant_id, serie, COALESCE(MAX(correlativo), 0)
FROM   guias_remision
GROUP  BY tenant_id, serie
ON CONFLICT (tenant_id, serie) DO UPDATE SET ultimo = EXCLUDED.ultimo;
