-- F-13: Optimistic locking para prevenir lost-update en actualizaciones concurrentes de stock
ALTER TABLE productos ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
