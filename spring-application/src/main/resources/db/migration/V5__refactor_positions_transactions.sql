-- ============================================================
-- V5: Refactor positions and transactions
-- Adds runner_id FK and new fields; migrates data via the
-- portfolio_id → runner_id mapping created in V4.
-- IG Seção 3.8 (V5)
-- ============================================================

-- ============================================================
-- 1. Refactor positions table
-- ============================================================

-- 1a. Add new UUID primary key (auto-generated per row)
ALTER TABLE positions ADD COLUMN id UUID NOT NULL DEFAULT gen_random_uuid();

-- 1b. Add runner_id (nullable for initial population)
ALTER TABLE positions ADD COLUMN runner_id UUID;

-- 1c. Add new domain columns
ALTER TABLE positions ADD COLUMN status        VARCHAR(20)              NOT NULL DEFAULT 'OPEN';
ALTER TABLE positions ADD COLUMN realized_pnl  NUMERIC(30,8)           NOT NULL DEFAULT 0;
ALTER TABLE positions ADD COLUMN closed_at     TIMESTAMP WITH TIME ZONE;
ALTER TABLE positions ADD COLUMN locked_by_transaction_id UUID;
ALTER TABLE positions ADD COLUMN locked_quantity           NUMERIC(30,8);
ALTER TABLE positions ADD COLUMN locked_at     TIMESTAMP WITH TIME ZONE;

-- 1d. Populate runner_id from portfolio_id → runner mapping
UPDATE positions p
SET runner_id = sr.id
FROM strategy_runners sr
WHERE sr.portfolio_id = p.portfolio_id;

-- 1e. Enforce NOT NULL on runner_id
ALTER TABLE positions ALTER COLUMN runner_id SET NOT NULL;

-- 1f. Change primary key: drop old (portfolio_id), add new (id)
ALTER TABLE positions DROP CONSTRAINT positions_pkey;
ALTER TABLE positions ADD CONSTRAINT positions_pkey PRIMARY KEY (id);

-- 1g. Remove column default — PK values are assigned by the application
ALTER TABLE positions ALTER COLUMN id DROP DEFAULT;

-- 1h. Add FK: runner_id → strategy_runners
ALTER TABLE positions ADD CONSTRAINT fk_positions_runner
    FOREIGN KEY (runner_id) REFERENCES strategy_runners(id);

-- 1i. Add FK: locked_by_transaction_id → transactions (nullable — no existing lock data)
ALTER TABLE positions ADD CONSTRAINT fk_positions_locked_tx
    FOREIGN KEY (locked_by_transaction_id) REFERENCES transactions(id);

-- ============================================================
-- 2. Refactor transactions table
-- ============================================================

-- 2a. Add runner_id (nullable for initial population)
ALTER TABLE transactions ADD COLUMN runner_id UUID;

-- 2b. Add new columns
ALTER TABLE transactions ADD COLUMN exchange_order_id VARCHAR(255);
ALTER TABLE transactions ADD COLUMN confidence        NUMERIC(3,2);
ALTER TABLE transactions ADD COLUMN reasoning         TEXT;

-- 2c. Populate runner_id from portfolio_id → runner mapping
UPDATE transactions t
SET runner_id = sr.id
FROM strategy_runners sr
WHERE sr.portfolio_id = t.portfolio_id;

-- 2d. Enforce NOT NULL on runner_id
ALTER TABLE transactions ALTER COLUMN runner_id SET NOT NULL;

-- 2e. Add FK: runner_id → strategy_runners
ALTER TABLE transactions ADD CONSTRAINT fk_transactions_runner
    FOREIGN KEY (runner_id) REFERENCES strategy_runners(id);

-- 2f. Partial UNIQUE index on client_order_id (non-null rows only).
--     Full UNIQUE NOT NULL constraint is applied after data cleanup in V7.
CREATE UNIQUE INDEX idx_transactions_client_order_id_unique
    ON transactions(client_order_id)
    WHERE client_order_id IS NOT NULL;

-- ============================================================
-- 3. Validation
-- ============================================================

DO $$
DECLARE
    v_positions_no_runner    BIGINT;
    v_transactions_no_runner BIGINT;
BEGIN
    SELECT COUNT(*) INTO v_positions_no_runner
    FROM positions WHERE runner_id IS NULL;

    SELECT COUNT(*) INTO v_transactions_no_runner
    FROM transactions WHERE runner_id IS NULL;

    IF v_positions_no_runner > 0 THEN
        RAISE EXCEPTION 'V5 validation failed: % position(s) with NULL runner_id',
            v_positions_no_runner;
    END IF;

    IF v_transactions_no_runner > 0 THEN
        RAISE EXCEPTION 'V5 validation failed: % transaction(s) with NULL runner_id',
            v_transactions_no_runner;
    END IF;
END $$;
