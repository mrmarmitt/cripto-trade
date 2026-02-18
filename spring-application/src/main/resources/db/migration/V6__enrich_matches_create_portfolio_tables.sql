-- ============================================================
-- V6: Enrich transaction_matches; create Portfolio aggregate tables
-- Adds denormalized columns to transaction_matches, creates
-- global_balances, margin_accounts, dust_accounts, dead_letter_entries.
-- IG Seção 3.8 (V6)
-- ============================================================

-- ============================================================
-- 1. Enrich transaction_matches
-- ============================================================

-- 1a. Add new PK and denormalized columns (nullable initially for population)
ALTER TABLE transaction_matches ADD COLUMN id                   UUID;
ALTER TABLE transaction_matches ADD COLUMN runner_id            UUID;
ALTER TABLE transaction_matches ADD COLUMN buy_price            NUMERIC(30,8);
ALTER TABLE transaction_matches ADD COLUMN sell_price           NUMERIC(30,8);
ALTER TABLE transaction_matches ADD COLUMN fee_amount           NUMERIC(30,8) NOT NULL DEFAULT 0;
ALTER TABLE transaction_matches ADD COLUMN fee_asset            VARCHAR(20);
ALTER TABLE transaction_matches ADD COLUMN fee_type             VARCHAR(20);
ALTER TABLE transaction_matches ADD COLUMN fee_converted_amount NUMERIC(30,8);
ALTER TABLE transaction_matches ADD COLUMN pnl_realized         NUMERIC(30,8);

-- 1b. Populate id
UPDATE transaction_matches SET id = gen_random_uuid();

-- 1c. Populate runner_id from buy transaction
UPDATE transaction_matches tm
SET runner_id = t.runner_id
FROM transactions t
WHERE t.id = tm.buy_transaction_id;

-- 1d. Populate buy_price from buy transaction (executed price, fallback to requested price)
UPDATE transaction_matches tm
SET buy_price = COALESCE(t.executed_price_amount, t.price_amount)
FROM transactions t
WHERE t.id = tm.buy_transaction_id;

-- 1e. Populate sell_price from sell transaction (executed price, fallback to requested price)
UPDATE transaction_matches tm
SET sell_price = COALESCE(t.executed_price_amount, t.price_amount)
FROM transactions t
WHERE t.id = tm.sell_transaction_id;

-- 1f. Populate fee data from sell transaction
UPDATE transaction_matches tm
SET fee_amount = t.fee_amount,
    fee_asset  = t.fee_currency,
    fee_type   = t.fee_type
FROM transactions t
WHERE t.id = tm.sell_transaction_id;

-- 1g. Calculate pnl_realized = (sell_price - buy_price) * matched_quantity - fee_amount
UPDATE transaction_matches
SET pnl_realized = ROUND(
    (sell_price - buy_price) * matched_quantity - fee_amount,
    8
);

-- 1h. Apply NOT NULL after population
ALTER TABLE transaction_matches ALTER COLUMN id          SET NOT NULL;
ALTER TABLE transaction_matches ALTER COLUMN runner_id   SET NOT NULL;
ALTER TABLE transaction_matches ALTER COLUMN buy_price   SET NOT NULL;
ALTER TABLE transaction_matches ALTER COLUMN sell_price  SET NOT NULL;
ALTER TABLE transaction_matches ALTER COLUMN fee_asset   SET NOT NULL;
ALTER TABLE transaction_matches ALTER COLUMN fee_type    SET NOT NULL;
ALTER TABLE transaction_matches ALTER COLUMN pnl_realized SET NOT NULL;

-- 1i. Change PK: drop composite PK (buy, sell), add single UUID PK
ALTER TABLE transaction_matches DROP CONSTRAINT transaction_matches_pkey;
ALTER TABLE transaction_matches ADD CONSTRAINT transaction_matches_pkey PRIMARY KEY (id);
ALTER TABLE transaction_matches ALTER COLUMN id DROP DEFAULT;

-- 1j. Add UNIQUE constraint on (buy, sell) pair
ALTER TABLE transaction_matches ADD CONSTRAINT uq_transaction_matches_pair
    UNIQUE (buy_transaction_id, sell_transaction_id);

-- 1k. Add FK: runner_id → strategy_runners
ALTER TABLE transaction_matches ADD CONSTRAINT fk_matches_runner
    FOREIGN KEY (runner_id) REFERENCES strategy_runners(id);

-- ============================================================
-- 2. Create global_balances table (replaces portfolio_balances)
-- ============================================================

CREATE TABLE global_balances (
    portfolio_id        UUID                     NOT NULL,
    available_balance   NUMERIC(30,8)            NOT NULL,
    reserved_balance    NUMERIC(30,8)            NOT NULL DEFAULT 0,
    realized_balance    NUMERIC(30,8)            NOT NULL DEFAULT 0,
    initial_capital     NUMERIC(30,8)            NOT NULL,
    base_currency       VARCHAR(20)              NOT NULL,
    total_fees_paid     NUMERIC(30,8)            NOT NULL DEFAULT 0,
    last_execution_time TIMESTAMP WITH TIME ZONE,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    version             BIGINT                            DEFAULT 0,
    CONSTRAINT global_balances_pkey       PRIMARY KEY (portfolio_id),
    CONSTRAINT fk_global_balances_portfolio FOREIGN KEY (portfolio_id) REFERENCES portfolios(id)
);

-- ============================================================
-- 3. Populate global_balances from portfolio_balances
--    available_balance  ← available_amount
--    reserved_balance   ← invested_amount  (semantics: "invested" → "reserved for in-flight orders")
--    realized_balance   ← realized_pnl
--    initial_capital    ← portfolios.initial_capital_amount (JOIN)
--    base_currency      ← portfolios.initial_capital_currency (JOIN)
--    total_fees_paid    ← 0 (no historical fee data)
-- ============================================================

INSERT INTO global_balances (
    portfolio_id,
    available_balance,
    reserved_balance,
    realized_balance,
    initial_capital,
    base_currency,
    total_fees_paid,
    last_execution_time,
    updated_at,
    version
)
SELECT
    pb.portfolio_id,
    pb.available_amount,
    pb.invested_amount,
    pb.realized_pnl,
    p.initial_capital_amount,
    p.initial_capital_currency,
    0,
    pb.last_execution_time,
    pb.updated_at,
    pb.version
FROM portfolio_balances pb
JOIN portfolios p ON p.id = pb.portfolio_id;

-- ============================================================
-- 4. Create margin_accounts table
-- ============================================================

CREATE TABLE margin_accounts (
    id               UUID                     NOT NULL,
    portfolio_id     UUID                     NOT NULL,
    exchange_id      VARCHAR(50)              NOT NULL,
    reserved_capital NUMERIC(30,8)            NOT NULL DEFAULT 0,
    total_fees_paid  NUMERIC(30,8)            NOT NULL DEFAULT 0,
    is_active        BOOLEAN                  NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    version          BIGINT                            DEFAULT 0,
    CONSTRAINT margin_accounts_pkey                        PRIMARY KEY (id),
    CONSTRAINT fk_margin_accounts_portfolio                FOREIGN KEY (portfolio_id) REFERENCES portfolios(id),
    CONSTRAINT uq_margin_accounts_portfolio_exchange       UNIQUE (portfolio_id, exchange_id)
);

-- ============================================================
-- 5. Create dust_accounts table
-- ============================================================

CREATE TABLE dust_accounts (
    id               UUID                     NOT NULL,
    portfolio_id     UUID                     NOT NULL,
    runner_id        UUID,
    source_type      VARCHAR(30)              NOT NULL,
    original_asset   VARCHAR(20)              NOT NULL,
    original_amount  NUMERIC(30,8)            NOT NULL,
    converted_amount NUMERIC(30,8),
    transaction_id   UUID,
    is_resolved      BOOLEAN                  NOT NULL DEFAULT FALSE,
    resolved_at      TIMESTAMP WITH TIME ZONE,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT dust_accounts_pkey             PRIMARY KEY (id),
    CONSTRAINT fk_dust_portfolio              FOREIGN KEY (portfolio_id)  REFERENCES portfolios(id),
    CONSTRAINT fk_dust_runner                 FOREIGN KEY (runner_id)     REFERENCES strategy_runners(id),
    CONSTRAINT fk_dust_transaction            FOREIGN KEY (transaction_id) REFERENCES transactions(id)
);

-- ============================================================
-- 6. Create dead_letter_entries table
-- ============================================================

CREATE TABLE dead_letter_entries (
    id                UUID                     NOT NULL,
    portfolio_id      UUID                     NOT NULL,
    client_order_id   VARCHAR(255),
    exchange_order_id VARCHAR(255),
    raw_payload       TEXT                     NOT NULL,
    reason            VARCHAR(50)              NOT NULL,
    is_resolved       BOOLEAN                  NOT NULL DEFAULT FALSE,
    resolved_by       VARCHAR(255),
    resolved_at       TIMESTAMP WITH TIME ZONE,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT dead_letter_entries_pkey PRIMARY KEY (id),
    CONSTRAINT fk_dlq_portfolio         FOREIGN KEY (portfolio_id) REFERENCES portfolios(id)
);

-- ============================================================
-- 7. Validation
-- ============================================================

DO $$
DECLARE
    v_balance_count        BIGINT;
    v_global_balance_count BIGINT;
    v_matches_no_runner    BIGINT;
    v_matches_no_id        BIGINT;
BEGIN
    SELECT COUNT(*) INTO v_balance_count        FROM portfolio_balances;
    SELECT COUNT(*) INTO v_global_balance_count FROM global_balances;
    SELECT COUNT(*) INTO v_matches_no_runner    FROM transaction_matches WHERE runner_id IS NULL;
    SELECT COUNT(*) INTO v_matches_no_id        FROM transaction_matches WHERE id IS NULL;

    IF v_balance_count != v_global_balance_count THEN
        RAISE EXCEPTION 'V6 validation failed: portfolio_balances=% != global_balances=%',
            v_balance_count, v_global_balance_count;
    END IF;

    IF v_matches_no_runner > 0 THEN
        RAISE EXCEPTION 'V6 validation failed: % match(es) with NULL runner_id',
            v_matches_no_runner;
    END IF;

    IF v_matches_no_id > 0 THEN
        RAISE EXCEPTION 'V6 validation failed: % match(es) with NULL id',
            v_matches_no_id;
    END IF;
END $$;
