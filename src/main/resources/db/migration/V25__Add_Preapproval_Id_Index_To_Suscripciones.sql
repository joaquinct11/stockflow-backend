-- Add index on preapproval_id for efficient preapproval webhook lookups
CREATE INDEX IF NOT EXISTS idx_suscripciones_preapproval_id ON suscripciones(preapproval_id);
