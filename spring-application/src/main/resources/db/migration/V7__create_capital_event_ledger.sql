-- ============================================================
-- V7: Capital event idempotency ledger
-- Prevents duplicate financial effects on retries/re-delivery.
-- ============================================================

CREATE TABLE capital_event_ledger (
    event_key  VARCHAR(255)             NOT NULL,
    event_type VARCHAR(50)              NOT NULL,
    event_id   UUID                     NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT capital_event_ledger_pkey PRIMARY KEY (event_key)
);

CREATE UNIQUE INDEX uq_capital_event_ledger_type_id
    ON capital_event_ledger(event_type, event_id);

CREATE INDEX idx_capital_event_ledger_created_at
    ON capital_event_ledger(created_at);
