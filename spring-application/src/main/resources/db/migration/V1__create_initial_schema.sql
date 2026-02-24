-- ============================================================
-- V1: CTrade Initial Schema (target state)
-- Two-aggregate model: Portfolio (financial authority) +
-- StrategyRunner (operational authority).
-- IG Seção 3.6
-- ============================================================

-- ============================================================
-- Portfolio aggregate
-- ============================================================

CREATE TABLE portfolios (
    id                   UUID                     NOT NULL,
    name                 VARCHAR(255)             NOT NULL,
    is_active            BOOLEAN                  NOT NULL DEFAULT TRUE,
    safe_mode_status     VARCHAR(20)              NOT NULL DEFAULT 'NORMAL',
    capital_pooling_mode VARCHAR(20)              NOT NULL DEFAULT 'SHARED',
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    version              BIGINT                            DEFAULT 0,
    CONSTRAINT portfolios_pkey      PRIMARY KEY (id),
    CONSTRAINT portfolios_name_key  UNIQUE (name)
);

CREATE INDEX idx_portfolios_is_active ON portfolios(is_active);

-- ============================================================
-- StrategyRunner aggregate
-- ============================================================

CREATE TABLE strategy_runners (
    id                     UUID                     NOT NULL,
    portfolio_id           UUID                     NOT NULL,
    short_code             VARCHAR(4)               NOT NULL,
    strategy_id            UUID                     NOT NULL,
    strategy_name          VARCHAR(255)             NOT NULL,
    symbol                 VARCHAR(50)              NOT NULL,
    exchange_id            VARCHAR(50)              NOT NULL,
    status                 VARCHAR(20)              NOT NULL DEFAULT 'CREATED',
    execution_policy       VARCHAR(20)              NOT NULL,
    accounting_policy_type VARCHAR(20)              NOT NULL DEFAULT 'FIFO',
    max_allocation_percent NUMERIC(5,4)             NOT NULL,
    max_open_positions     INTEGER                  NOT NULL DEFAULT 1,
    max_pending_orders     INTEGER                  NOT NULL DEFAULT 1,
    dedicated_budget       NUMERIC(30,8),
    is_reconciling         BOOLEAN                  NOT NULL DEFAULT FALSE,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    last_reconciliation_at TIMESTAMP WITH TIME ZONE,
    archived_at            TIMESTAMP WITH TIME ZONE,
    version                BIGINT                            DEFAULT 0,
    CONSTRAINT strategy_runners_pkey           PRIMARY KEY (id),
    CONSTRAINT strategy_runners_short_code_key UNIQUE (short_code),
    CONSTRAINT fk_runners_portfolio            FOREIGN KEY (portfolio_id) REFERENCES portfolios(id)
);

CREATE INDEX idx_runners_portfolio ON strategy_runners(portfolio_id);
CREATE INDEX idx_runners_status    ON strategy_runners(status);

-- Active runners: at most one active runner per (strategy, symbol, exchange, portfolio)
CREATE UNIQUE INDEX idx_runners_active_unique
    ON strategy_runners(strategy_id, symbol, exchange_id, portfolio_id)
    WHERE status NOT IN ('ARCHIVED', 'TERMINATING');

CREATE TABLE runner_market_data_sources (
    runner_id UUID        NOT NULL,
    source    VARCHAR(50) NOT NULL,
    CONSTRAINT runner_market_data_sources_pkey PRIMARY KEY (runner_id, source),
    CONSTRAINT fk_mds_runner FOREIGN KEY (runner_id) REFERENCES strategy_runners(id) ON DELETE CASCADE
);

-- ============================================================
-- Portfolio financial tables
-- ============================================================

-- Global balance: available, reserved and realized capital per Portfolio
CREATE TABLE global_balances (
    portfolio_id        UUID                     NOT NULL,
    available_balance   NUMERIC(30,8)            NOT NULL,
    reserved_balance    NUMERIC(30,8)            NOT NULL DEFAULT 0,
    realized_balance    NUMERIC(30,8)            NOT NULL DEFAULT 0,
    initial_capital     NUMERIC(30,8)            NOT NULL,
    base_currency       VARCHAR(20)              NOT NULL,
    total_fees_paid     NUMERIC(30,8)            NOT NULL DEFAULT 0,
    last_execution_time TIMESTAMP WITH TIME ZONE,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    version             BIGINT                            DEFAULT 0,
    CONSTRAINT global_balances_pkey        PRIMARY KEY (portfolio_id),
    CONSTRAINT fk_global_balances_portfolio FOREIGN KEY (portfolio_id) REFERENCES portfolios(id)
);

-- Margin account: reserved capital per exchange per Portfolio
CREATE TABLE margin_accounts (
    id               UUID                     NOT NULL,
    portfolio_id     UUID                     NOT NULL,
    exchange_id      VARCHAR(50)              NOT NULL,
    reserved_capital NUMERIC(30,8)            NOT NULL DEFAULT 0,
    total_fees_paid  NUMERIC(30,8)            NOT NULL DEFAULT 0,
    is_active        BOOLEAN                  NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    version          BIGINT                            DEFAULT 0,
    CONSTRAINT margin_accounts_pkey                  PRIMARY KEY (id),
    CONSTRAINT fk_margin_accounts_portfolio          FOREIGN KEY (portfolio_id) REFERENCES portfolios(id),
    CONSTRAINT uq_margin_accounts_portfolio_exchange UNIQUE (portfolio_id, exchange_id)
);

-- Dust account: cross-currency fee residuals pending conversion
CREATE TABLE dust_accounts (
    id               UUID                     NOT NULL,
    portfolio_id     UUID                     NOT NULL,
    runner_id        UUID,
    source_type      VARCHAR(30)              NOT NULL,
    original_asset   VARCHAR(20)              NOT NULL,
    original_amount  NUMERIC(30,8)            NOT NULL,
    converted_amount NUMERIC(30,8),
    transaction_id   UUID,
    is_resolved      BOOLEAN                  NOT NULL DEFAULT FALSE,
    resolved_at      TIMESTAMP WITH TIME ZONE,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT dust_accounts_pkey  PRIMARY KEY (id),
    CONSTRAINT fk_dust_portfolio   FOREIGN KEY (portfolio_id) REFERENCES portfolios(id),
    CONSTRAINT fk_dust_runner      FOREIGN KEY (runner_id)    REFERENCES strategy_runners(id)
    -- fk_dust_transaction added after transactions table is created (circular dependency)
);

CREATE INDEX idx_dust_portfolio_unresolved
    ON dust_accounts(portfolio_id, is_resolved)
    WHERE is_resolved = FALSE;

CREATE INDEX idx_dust_runner ON dust_accounts(runner_id);

-- Dead Letter Queue: orphaned or unroutable exchange callbacks
CREATE TABLE dead_letter_entries (
    id                UUID                     NOT NULL,
    portfolio_id      UUID                     NOT NULL,
    client_order_id   VARCHAR(255),
    exchange_order_id VARCHAR(255),
    raw_payload       TEXT                     NOT NULL,
    reason            VARCHAR(50)              NOT NULL,
    is_resolved       BOOLEAN                  NOT NULL DEFAULT FALSE,
    resolved_by       VARCHAR(255),
    resolved_at       TIMESTAMP WITH TIME ZONE,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT dead_letter_entries_pkey PRIMARY KEY (id),
    CONSTRAINT fk_dlq_portfolio         FOREIGN KEY (portfolio_id) REFERENCES portfolios(id)
);

CREATE INDEX idx_dlq_portfolio_unresolved
    ON dead_letter_entries(portfolio_id, is_resolved)
    WHERE is_resolved = FALSE;

-- ============================================================
-- StrategyRunner operational tables
-- Positions and Transactions have a circular FK dependency:
--   positions.locked_by_transaction_id → transactions(id)
--   transactions.target_lot_id          → positions(id)
-- Both tables are created first; FKs are added via ALTER TABLE below.
-- ============================================================

-- Positions: one open lot per (runner, symbol), with provisional lock support
CREATE TABLE positions (
    id                        UUID                     NOT NULL,
    runner_id                 UUID                     NOT NULL,
    symbol                    VARCHAR(50)              NOT NULL,
    status                    VARCHAR(20)              NOT NULL DEFAULT 'OPEN',
    quantity                  NUMERIC(30,8)            NOT NULL,
    average_price             NUMERIC(30,8)            NOT NULL,
    current_price             NUMERIC(30,8),
    realized_pnl              NUMERIC(30,8)            NOT NULL DEFAULT 0,
    opened_at                 TIMESTAMP WITH TIME ZONE NOT NULL,
    closed_at                 TIMESTAMP WITH TIME ZONE,
    locked_by_transaction_id  UUID,
    locked_quantity           NUMERIC(30,8),
    locked_at                 TIMESTAMP WITH TIME ZONE,
    updated_at                TIMESTAMP WITH TIME ZONE NOT NULL,
    version                   BIGINT                            DEFAULT 0,
    CONSTRAINT positions_pkey    PRIMARY KEY (id),
    CONSTRAINT fk_positions_runner FOREIGN KEY (runner_id) REFERENCES strategy_runners(id)
    -- fk_positions_locked_tx added after transactions table is created
);

CREATE INDEX idx_positions_runner ON positions(runner_id);
CREATE INDEX idx_positions_status ON positions(status);

-- Open positions by runner (hot path: execution policy, signal routing)
CREATE INDEX idx_positions_runner_open
    ON positions(runner_id, status)
    WHERE status = 'OPEN';

-- Locked positions (Watchdog, Boot Sequence unlock)
CREATE INDEX idx_positions_locked_tx
    ON positions(locked_by_transaction_id)
    WHERE locked_by_transaction_id IS NOT NULL;

-- Transactions: order lifecycle managed by StrategyRunner
CREATE TABLE transactions (
    id                UUID                     NOT NULL,
    runner_id         UUID                     NOT NULL,
    client_order_id   VARCHAR(255)             NOT NULL,
    exchange_order_id VARCHAR(255),
    status            VARCHAR(50)              NOT NULL,
    type              VARCHAR(20)              NOT NULL,
    symbol            VARCHAR(50)              NOT NULL,
    quantity          NUMERIC(30,8)            NOT NULL,
    executed_quantity NUMERIC(30,8),
    price             NUMERIC(30,8)            NOT NULL,
    executed_price    NUMERIC(30,8),
    total             NUMERIC(30,8)            NOT NULL,
    confidence        NUMERIC(3,2),
    reasoning         TEXT,
    target_lot_id     UUID,
    requested_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    executed_at       TIMESTAMP WITH TIME ZONE,
    reject_reason     TEXT,
    version           BIGINT                            DEFAULT 0,
    CONSTRAINT transactions_pkey              PRIMARY KEY (id),
    CONSTRAINT transactions_client_order_id_key UNIQUE (client_order_id),
    CONSTRAINT fk_transactions_runner        FOREIGN KEY (runner_id) REFERENCES strategy_runners(id)
    -- fk_transactions_target_lot added after positions table is created
);

CREATE INDEX idx_transactions_client_order_id ON transactions(client_order_id);
CREATE INDEX idx_transactions_status          ON transactions(status);
CREATE INDEX idx_transactions_type            ON transactions(type);
CREATE INDEX idx_transactions_requested_at    ON transactions(requested_at);
CREATE INDEX idx_transactions_runner          ON transactions(runner_id);

-- In-flight transactions by runner (Boot Sequence Step 2 + Watchdog)
CREATE INDEX idx_transactions_inflight
    ON transactions(runner_id, status)
    WHERE status IN ('PENDING', 'SUBMITTED', 'PARTIAL');

-- ============================================================
-- Resolve circular FK dependencies
-- ============================================================

ALTER TABLE positions ADD CONSTRAINT fk_positions_locked_tx
    FOREIGN KEY (locked_by_transaction_id) REFERENCES transactions(id);

ALTER TABLE transactions ADD CONSTRAINT fk_transactions_target_lot
    FOREIGN KEY (target_lot_id) REFERENCES positions(id);

ALTER TABLE dust_accounts ADD CONSTRAINT fk_dust_transaction
    FOREIGN KEY (transaction_id) REFERENCES transactions(id);

-- ============================================================
-- TransactionMatch: immutable execution record
-- ============================================================

-- Immutable record of a matched buy–sell execution
CREATE TABLE transaction_matches (
    id                   UUID                     NOT NULL,
    runner_id            UUID                     NOT NULL,
    buy_transaction_id   UUID                     NOT NULL,
    sell_transaction_id  UUID                     NOT NULL,
    matched_quantity     NUMERIC(30,8)            NOT NULL,
    buy_price            NUMERIC(30,8)            NOT NULL,
    sell_price           NUMERIC(30,8)            NOT NULL,
    fee_amount           NUMERIC(30,8)            NOT NULL DEFAULT 0,
    fee_asset            VARCHAR(20)              NOT NULL,
    fee_type             VARCHAR(20)              NOT NULL,
    fee_converted_amount NUMERIC(30,8),
    pnl_realized         NUMERIC(30,8)            NOT NULL,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT transaction_matches_pkey       PRIMARY KEY (id),
    CONSTRAINT uq_transaction_matches_pair    UNIQUE (buy_transaction_id, sell_transaction_id),
    CONSTRAINT fk_matches_runner              FOREIGN KEY (runner_id)           REFERENCES strategy_runners(id),
    CONSTRAINT fk_matches_buy_transaction     FOREIGN KEY (buy_transaction_id)  REFERENCES transactions(id),
    CONSTRAINT fk_matches_sell_transaction    FOREIGN KEY (sell_transaction_id) REFERENCES transactions(id),
    CONSTRAINT chk_matches_quantity           CHECK (matched_quantity > 0),
    CONSTRAINT chk_matches_buy_price          CHECK (buy_price > 0),
    CONSTRAINT chk_matches_sell_price         CHECK (sell_price > 0)
);

CREATE INDEX idx_matches_runner ON transaction_matches(runner_id);
CREATE INDEX idx_matches_buy    ON transaction_matches(buy_transaction_id);
CREATE INDEX idx_matches_sell   ON transaction_matches(sell_transaction_id);
