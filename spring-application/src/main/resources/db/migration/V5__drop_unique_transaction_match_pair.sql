-- Allow multiple matches per buy/sell pair (partial fills)
ALTER TABLE transaction_matches
    DROP CONSTRAINT IF EXISTS uq_transaction_matches_pair;
