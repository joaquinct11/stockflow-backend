-- Agrega culqi_customer_id a suscripciones para correlación directa de webhooks.
-- El payload de charge.creation.succeeded incluye customer_id (cus_xxx) pero no
-- subscription_id (sxn_xxx), por lo que se necesita este campo para buscarSuscripcion()
-- sin depender de un round-trip a Culqi ni del campo email en el payload.
ALTER TABLE suscripciones
    ADD COLUMN IF NOT EXISTS culqi_customer_id VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_suscripciones_culqi_customer_id
    ON suscripciones (culqi_customer_id);
