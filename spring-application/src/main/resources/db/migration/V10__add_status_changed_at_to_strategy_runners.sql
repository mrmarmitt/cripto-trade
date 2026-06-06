ALTER TABLE strategy_runners
    ADD COLUMN IF NOT EXISTS status_changed_at TIMESTAMPTZ;

UPDATE strategy_runners
   SET status_changed_at = created_at
 WHERE status_changed_at IS NULL;

ALTER TABLE strategy_runners
    ALTER COLUMN status_changed_at SET NOT NULL;
