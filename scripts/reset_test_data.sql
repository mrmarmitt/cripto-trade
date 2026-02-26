-- Reset runner trading data for fresh tests
BEGIN;

-- Clear position links before deletion (explicit, for safety)
UPDATE positions
   SET locked_by_transaction_id = NULL,
       locked_quantity = NULL,
       locked_at = NULL,
       opened_by_transaction_id = NULL;

-- Remove execution records first (FK-safe order)
DELETE FROM transaction_matches;
DELETE FROM transactions;
DELETE FROM positions;

-- Restore GlobalBalance to initial state
UPDATE global_balances
   SET available_balance = initial_capital,
       reserved_balance = 0,
       realized_balance = 0,
       total_fees_paid = 0,
       last_execution_time = NULL,
       updated_at = NOW();

COMMIT;
