-- ============================================================
-- V7: Cleanup — remove obsolete columns and tables
-- Runs AFTER V4–V6 have fully migrated all data.
-- IG Seção 3.8 (V7)
-- ============================================================

-- ============================================================
-- 0. Pre-check: ensure all transactions have client_order_id
--    before enforcing NOT NULL constraint.
-- ============================================================

DO $$
DECLARE
    v_null_count BIGINT;
BEGIN
    SELECT COUNT(*) INTO v_null_count
    FROM transactions WHERE client_order_id IS NULL;

    IF v_null_count > 0 THEN
        RAISE EXCEPTION 'V7 pre-check failed: % transaction(s) have NULL client_order_id — resolve before migration',
            v_null_count;
    END IF;
END $$;

-- ============================================================
-- 1. portfolios: drop columns moved to strategy_runners / global_balances;
--               add new status columns
-- ============================================================

-- Dropped: moved to strategy_runners
ALTER TABLE portfolios DROP COLUMN strategy_id;
ALTER TABLE portfolios DROP COLUMN strategy_name;
ALTER TABLE portfolios DROP COLUMN symbol;
ALTER TABLE portfolios DROP COLUMN order_execution_exchange;

-- Dropped: moved to global_balances
ALTER TABLE portfolios DROP COLUMN initial_capital_amount;
ALTER TABLE portfolios DROP COLUMN initial_capital_currency;

-- New Portfolio status columns (IG Seção 3.6)
ALTER TABLE portfolios ADD COLUMN safe_mode_status    VARCHAR(20) NOT NULL DEFAULT 'NORMAL';
ALTER TABLE portfolios ADD COLUMN capital_pooling_mode VARCHAR(20) NOT NULL DEFAULT 'SHARED';

-- ============================================================
-- 2. positions: rename simplified columns, drop obsolete columns
-- ============================================================

-- Rename *_amount → plain names (currency now inferred from symbol)
ALTER TABLE positions RENAME COLUMN quantity_amount      TO quantity;
ALTER TABLE positions RENAME COLUMN average_price_amount TO average_price;
ALTER TABLE positions RENAME COLUMN current_price_amount TO current_price;

-- Drop currency columns
ALTER TABLE positions DROP COLUMN quantity_currency;
ALTER TABLE positions DROP COLUMN average_price_currency;
ALTER TABLE positions DROP COLUMN current_price_currency;

-- Drop portfolio_id: CASCADE removes FK constraint positions_portfolio_id_fkey
ALTER TABLE positions DROP COLUMN portfolio_id CASCADE;

-- ============================================================
-- 3. transactions: rename, drop obsolete columns, fix target_lot_id FK
-- ============================================================

-- Rename *_amount → plain names
ALTER TABLE transactions RENAME COLUMN quantity_amount          TO quantity;
ALTER TABLE transactions RENAME COLUMN executed_quantity_amount TO executed_quantity;
ALTER TABLE transactions RENAME COLUMN price_amount             TO price;
ALTER TABLE transactions RENAME COLUMN executed_price_amount    TO executed_price;
ALTER TABLE transactions RENAME COLUMN total_amount             TO total;

-- Drop quantity *_currency / *_type columns
ALTER TABLE transactions DROP COLUMN quantity_currency;
ALTER TABLE transactions DROP COLUMN quantity_type;
ALTER TABLE transactions DROP COLUMN executed_quantity_currency;
ALTER TABLE transactions DROP COLUMN executed_quantity_type;

-- Drop price *_currency / *_type columns
ALTER TABLE transactions DROP COLUMN price_currency;
ALTER TABLE transactions DROP COLUMN price_type;
ALTER TABLE transactions DROP COLUMN executed_price_currency;
ALTER TABLE transactions DROP COLUMN executed_price_type;

-- Drop total *_currency / *_type columns
ALTER TABLE transactions DROP COLUMN total_currency;
ALTER TABLE transactions DROP COLUMN total_type;

-- Drop fee columns (migrated to transaction_matches in V6)
ALTER TABLE transactions DROP COLUMN fee_amount;
ALTER TABLE transactions DROP COLUMN fee_currency;
ALTER TABLE transactions DROP COLUMN fee_type;

-- Drop portfolio_id: CASCADE removes FK + idx_transactions_portfolio_id
ALTER TABLE transactions DROP COLUMN portfolio_id CASCADE;

-- Fix target_lot_id FK: old data references transaction IDs — not meaningful in new model
-- Null out, then re-point to positions(id) per IG Seção 3.6
UPDATE transactions SET target_lot_id = NULL WHERE target_lot_id IS NOT NULL;
ALTER TABLE transactions DROP CONSTRAINT transactions_target_lot_id_fkey;
ALTER TABLE transactions ADD CONSTRAINT fk_transactions_target_lot
    FOREIGN KEY (target_lot_id) REFERENCES positions(id);

-- Enforce NOT NULL on client_order_id (all transactions must have a clientOrderId)
ALTER TABLE transactions ALTER COLUMN client_order_id SET NOT NULL;

-- ============================================================
-- 4. Drop portfolio_balances (replaced by global_balances in V6)
-- ============================================================

DROP TABLE portfolio_balances;

-- ============================================================
-- 5. Drop portfolio_market_data_sources (replaced by runner_market_data_sources in V4)
-- ============================================================

DROP TABLE portfolio_market_data_sources;

-- ============================================================
-- 6. Drop obsolete indexes (may already be dropped by CASCADE column drops above)
-- ============================================================

DROP INDEX IF EXISTS idx_portfolios_symbol;
DROP INDEX IF EXISTS idx_portfolios_strategy_id;
DROP INDEX IF EXISTS idx_transactions_portfolio_id;

-- ============================================================
-- 7. Validation
-- ============================================================

DO $$
DECLARE
    v_null_coi          BIGINT;
    v_positions_pf_col  BIGINT;
    v_tx_pf_col         BIGINT;
BEGIN
    SELECT COUNT(*) INTO v_null_coi FROM transactions WHERE client_order_id IS NULL;

    IF v_null_coi > 0 THEN
        RAISE EXCEPTION 'V7 validation failed: % transaction(s) still have NULL client_order_id',
            v_null_coi;
    END IF;

    -- Verify portfolio_id was removed from positions
    SELECT COUNT(*) INTO v_positions_pf_col
    FROM information_schema.columns
    WHERE table_name = 'positions' AND column_name = 'portfolio_id';

    IF v_positions_pf_col > 0 THEN
        RAISE EXCEPTION 'V7 validation failed: portfolio_id column still exists in positions';
    END IF;

    -- Verify portfolio_id was removed from transactions
    SELECT COUNT(*) INTO v_tx_pf_col
    FROM information_schema.columns
    WHERE table_name = 'transactions' AND column_name = 'portfolio_id';

    IF v_tx_pf_col > 0 THEN
        RAISE EXCEPTION 'V7 validation failed: portfolio_id column still exists in transactions';
    END IF;
END $$;
