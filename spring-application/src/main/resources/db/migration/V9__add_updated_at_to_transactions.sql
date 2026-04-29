ALTER TABLE transactions
    ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE;

UPDATE transactions
SET updated_at = COALESCE(executed_at, requested_at)
WHERE updated_at IS NULL;

ALTER TABLE transactions
    ALTER COLUMN updated_at SET NOT NULL;

CREATE INDEX idx_transactions_updated_at ON transactions(updated_at);
