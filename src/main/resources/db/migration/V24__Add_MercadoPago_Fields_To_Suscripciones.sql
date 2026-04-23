ALTER TABLE suscripciones
    ADD COLUMN IF NOT EXISTS mp_preference_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS mp_payment_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS current_period_start TIMESTAMP,
    ADD COLUMN IF NOT EXISTS current_period_end TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_suscripciones_mp_preference_id ON suscripciones(mp_preference_id);
CREATE INDEX IF NOT EXISTS idx_suscripciones_mp_payment_id ON suscripciones(mp_payment_id);
