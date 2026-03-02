-- Enforce position origin link for active positions
-- OPEN/CLOSING positions must always be traceable to the BUY transaction that opened them.
ALTER TABLE positions
    ADD CONSTRAINT ck_positions_active_requires_opened_tx
        CHECK (
            status NOT IN ('OPEN', 'CLOSING')
                OR opened_by_transaction_id IS NOT NULL
            );

-- Ensure opened_by_transaction_id references an existing transaction
ALTER TABLE positions
    ADD CONSTRAINT fk_positions_opened_by_tx
        FOREIGN KEY (opened_by_transaction_id) REFERENCES transactions(id);
