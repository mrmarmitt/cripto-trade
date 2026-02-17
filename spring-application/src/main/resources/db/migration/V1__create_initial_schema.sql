-- ============================================================
-- V1: CTrade Initial Schema
-- ============================================================

-- Portfolios (configuração - escrita rara)
CREATE TABLE portfolios (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    strategy_id UUID NOT NULL,
    strategy_name VARCHAR(255) NOT NULL,
    symbol VARCHAR(50) NOT NULL,
    initial_capital_amount NUMERIC(30, 8) NOT NULL,
    initial_capital_currency VARCHAR(20) NOT NULL,
    order_execution_exchange VARCHAR(50) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT DEFAULT 0
);

-- Portfolio market data sources
CREATE TABLE portfolio_market_data_sources (
    portfolio_id UUID NOT NULL REFERENCES portfolios(id) ON DELETE CASCADE,
    source VARCHAR(50) NOT NULL,
    PRIMARY KEY (portfolio_id, source)
);

-- Portfolio balances (estado financeiro - escrita frequente)
CREATE TABLE portfolio_balances (
    portfolio_id UUID PRIMARY KEY REFERENCES portfolios(id) ON DELETE CASCADE,
    available_amount NUMERIC(30, 8) NOT NULL,
    available_currency VARCHAR(20) NOT NULL,
    invested_amount NUMERIC(30, 8) NOT NULL,
    invested_currency VARCHAR(20) NOT NULL,
    realized_pnl NUMERIC(30, 8) NOT NULL DEFAULT 0,
    last_execution_time TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT DEFAULT 0
);

-- Positions (estado de posição - escrita muito frequente)
CREATE TABLE positions (
    portfolio_id UUID PRIMARY KEY REFERENCES portfolios(id) ON DELETE CASCADE,
    symbol VARCHAR(50) NOT NULL,
    quantity_amount NUMERIC(30, 8) NOT NULL,
    quantity_currency VARCHAR(20) NOT NULL,
    average_price_amount NUMERIC(30, 8) NOT NULL,
    average_price_currency VARCHAR(20) NOT NULL,
    current_price_amount NUMERIC(30, 8),
    current_price_currency VARCHAR(20),
    opened_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT DEFAULT 0
);

-- Transactions (auditoria - append only)
CREATE TABLE transactions (
    id UUID PRIMARY KEY,
    portfolio_id UUID NOT NULL REFERENCES portfolios(id) ON DELETE CASCADE,
    client_order_id VARCHAR(255),
    status VARCHAR(50) NOT NULL,
    type VARCHAR(20) NOT NULL,
    symbol VARCHAR(50) NOT NULL,
    quantity_amount NUMERIC(30, 8) NOT NULL,
    quantity_currency VARCHAR(20) NOT NULL,
    quantity_type VARCHAR(30) NOT NULL,
    executed_quantity_amount NUMERIC(30, 8),
    executed_quantity_currency VARCHAR(20),
    executed_quantity_type VARCHAR(30),
    price_amount NUMERIC(30, 8) NOT NULL,
    price_currency VARCHAR(20) NOT NULL,
    price_type VARCHAR(30) NOT NULL,
    executed_price_amount NUMERIC(30, 8),
    executed_price_currency VARCHAR(20),
    executed_price_type VARCHAR(30),
    total_amount NUMERIC(30, 8) NOT NULL,
    total_currency VARCHAR(20) NOT NULL,
    total_type VARCHAR(30) NOT NULL,
    fee_amount NUMERIC(30, 8) NOT NULL,
    fee_currency VARCHAR(20) NOT NULL,
    fee_type VARCHAR(30) NOT NULL,
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    executed_at TIMESTAMP WITH TIME ZONE,
    reject_reason TEXT,
    version BIGINT DEFAULT 0
);

-- Indexes
CREATE INDEX idx_portfolios_symbol ON portfolios(symbol);
CREATE INDEX idx_portfolios_strategy_id ON portfolios(strategy_id);
CREATE INDEX idx_portfolios_is_active ON portfolios(is_active);

CREATE INDEX idx_transactions_portfolio_id ON transactions(portfolio_id);
CREATE INDEX idx_transactions_client_order_id ON transactions(client_order_id);
CREATE INDEX idx_transactions_status ON transactions(status);
CREATE INDEX idx_transactions_type ON transactions(type);
CREATE INDEX idx_transactions_requested_at ON transactions(requested_at);

CREATE INDEX idx_positions_symbol ON positions(symbol);
