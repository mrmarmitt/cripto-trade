-- Add opened_by_transaction_id to positions for BUY->SELL matching
ALTER TABLE positions
    ADD COLUMN opened_by_transaction_id UUID;

CREATE INDEX idx_positions_opened_by_tx
    ON positions(opened_by_transaction_id);
