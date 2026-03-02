-- Reset runner trading data for fresh tests
BEGIN;

-- Break FK cycle:
-- transactions.target_lot_id -> positions.id
-- positions.(opened_by/locked_by)_transaction_id -> transactions.id
-- We cannot null opened_by_transaction_id due active-position invariant (V6).
UPDATE transactions
   SET target_lot_id = NULL
 WHERE target_lot_id IS NOT NULL;

-- Clear transient lock fields (safe and explicit)
UPDATE positions
   SET locked_by_transaction_id = NULL,
       locked_quantity = NULL,
       locked_at = NULL
 WHERE locked_by_transaction_id IS NOT NULL
    OR locked_quantity IS NOT NULL
    OR locked_at IS NOT NULL;

-- Remove execution records first (FK-safe order)
DELETE FROM transaction_matches;
DELETE FROM positions;
DELETE FROM transactions;

-- Restore GlobalBalance to initial state
UPDATE global_balances
   SET available_balance = initial_capital,
       reserved_balance = 0,
       realized_balance = 0,
       total_fees_paid = 0,
       last_execution_time = NULL,
       updated_at = NOW();

COMMIT;
