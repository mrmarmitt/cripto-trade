-- T45: tipo da ordem na transacao (PANIC_SELL envia MARKET) e estado/auditoria do Safe Mode.

ALTER TABLE transactions
    ADD COLUMN order_type VARCHAR(20) NOT NULL DEFAULT 'LIMIT';

ALTER TABLE portfolios
    ADD COLUMN safe_mode_origin     VARCHAR(20),
    ADD COLUMN safe_mode_reason     VARCHAR(500),
    ADD COLUMN safe_mode_changed_at TIMESTAMP WITH TIME ZONE;

CREATE TABLE safe_mode_changes (
    id           UUID                     NOT NULL,
    portfolio_id UUID                     NOT NULL,
    from_status  VARCHAR(20)              NOT NULL,
    to_status    VARCHAR(20)              NOT NULL,
    origin       VARCHAR(20)              NOT NULL,
    reason       VARCHAR(500)             NOT NULL,
    changed_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT safe_mode_changes_pkey         PRIMARY KEY (id),
    CONSTRAINT fk_safe_mode_changes_portfolio FOREIGN KEY (portfolio_id) REFERENCES portfolios(id)
);

CREATE INDEX idx_safe_mode_changes_portfolio ON safe_mode_changes(portfolio_id, changed_at);
