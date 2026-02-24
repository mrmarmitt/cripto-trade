-- ============================================================
-- V2: Transaction Matches (FIFO buy-sell linking)
-- ============================================================

CREATE TABLE transaction_matches (
    buy_transaction_id UUID NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    sell_transaction_id UUID NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    matched_quantity NUMERIC(30, 8) NOT NULL CHECK (matched_quantity > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (buy_transaction_id, sell_transaction_id)
);

CREATE INDEX idx_transaction_matches_buy_id ON transaction_matches(buy_transaction_id);
CREATE INDEX idx_transaction_matches_sell_id ON transaction_matches(sell_transaction_id);
