-- ============================================================
-- V8: Add runner-scoped DLQ support
-- Enables granular boot gating by runner instead of whole portfolio.
-- ============================================================

ALTER TABLE dead_letter_entries
    ADD COLUMN runner_id UUID;

ALTER TABLE dead_letter_entries
    ADD CONSTRAINT fk_dlq_runner
    FOREIGN KEY (runner_id) REFERENCES strategy_runners(id);

CREATE INDEX idx_dlq_runner_unresolved
    ON dead_letter_entries(runner_id, is_resolved)
    WHERE is_resolved = FALSE AND runner_id IS NOT NULL;

CREATE INDEX idx_dlq_portfolio_unresolved_no_runner
    ON dead_letter_entries(portfolio_id, is_resolved)
    WHERE is_resolved = FALSE AND runner_id IS NULL;
