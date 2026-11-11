-- Agrega plan_codigo (nullable) a suscripciones para vincularlas al catálogo.
-- Las suscripciones existentes quedan con plan_codigo = NULL y siguen usando
-- la lógica legada de plan_id (BASICO/PRO). Solo las nuevas suscripciones
-- deben llenar este campo con un código válido del catálogo.
-- No se realiza backfill masivo en esta fase.

ALTER TABLE suscripciones
    ADD COLUMN IF NOT EXISTS plan_codigo VARCHAR(50)
        REFERENCES plan_catalogo(codigo);

COMMENT ON COLUMN suscripciones.plan_codigo IS
    'Código del plan comercial (plan_catalogo.codigo). NULL en suscripciones previas al catálogo.';
