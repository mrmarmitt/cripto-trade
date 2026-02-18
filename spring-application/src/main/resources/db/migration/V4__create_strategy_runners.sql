-- ============================================================
-- V4: Create StrategyRunner infrastructure
-- Creates strategy_runners and runner_market_data_sources,
-- then populates from existing portfolios and portfolio_market_data_sources.
-- IG Seção 3.8 (V4)
-- ============================================================

-- ============================================================
-- 1. Create strategy_runners table
-- ============================================================

CREATE TABLE strategy_runners (
    id                     UUID                     NOT NULL,
    portfolio_id           UUID                     NOT NULL REFERENCES portfolios(id),
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
    CONSTRAINT strategy_runners_pkey      PRIMARY KEY (id),
    CONSTRAINT strategy_runners_short_code_key UNIQUE (short_code)
);

-- ============================================================
-- 2. Create runner_market_data_sources table
-- ============================================================

CREATE TABLE runner_market_data_sources (
    runner_id UUID        NOT NULL REFERENCES strategy_runners(id) ON DELETE CASCADE,
    source    VARCHAR(50) NOT NULL,
    CONSTRAINT runner_market_data_sources_pkey PRIMARY KEY (runner_id, source)
);

-- ============================================================
-- 3. Populate strategy_runners from portfolios
--    shortCode: zero-padded 3-digit sequence ordered by created_at
--    status   : ACTIVE for active portfolios, ARCHIVED for inactive
--    defaults : executionPolicy=SINGLE, accountingPolicyType=FIFO,
--               maxAllocationPercent=1.0 (no limit)
-- ============================================================

INSERT INTO strategy_runners (
    id,
    portfolio_id,
    short_code,
    strategy_id,
    strategy_name,
    symbol,
    exchange_id,
    status,
    execution_policy,
    accounting_policy_type,
    max_allocation_percent,
    max_open_positions,
    max_pending_orders,
    dedicated_budget,
    is_reconciling,
    created_at,
    version
)
SELECT
    gen_random_uuid(),
    p.id,
    LPAD(ROW_NUMBER() OVER (ORDER BY p.created_at, p.id)::TEXT, 3, '0'),
    p.strategy_id,
    p.strategy_name,
    p.symbol,
    p.order_execution_exchange,
    CASE WHEN p.is_active THEN 'ACTIVE' ELSE 'ARCHIVED' END,
    'SINGLE',
    'FIFO',
    1.0000,
    1,
    1,
    NULL,
    FALSE,
    p.created_at,
    0
FROM portfolios p;

-- ============================================================
-- 4. Populate runner_market_data_sources from portfolio_market_data_sources
-- ============================================================

INSERT INTO runner_market_data_sources (runner_id, source)
SELECT sr.id, pmds.source
FROM portfolio_market_data_sources pmds
JOIN strategy_runners sr ON sr.portfolio_id = pmds.portfolio_id;

-- ============================================================
-- 5. Validation
-- ============================================================

DO $$
DECLARE
    v_portfolio_count  BIGINT;
    v_runner_count     BIGINT;
    v_mds_count        BIGINT;
    v_runner_mds_count BIGINT;
BEGIN
    SELECT COUNT(*) INTO v_portfolio_count  FROM portfolios;
    SELECT COUNT(*) INTO v_runner_count     FROM strategy_runners;
    SELECT COUNT(*) INTO v_mds_count        FROM portfolio_market_data_sources;
    SELECT COUNT(*) INTO v_runner_mds_count FROM runner_market_data_sources;

    IF v_portfolio_count != v_runner_count THEN
        RAISE EXCEPTION 'V4 validation failed: portfolio_count=% != runner_count=%',
            v_portfolio_count, v_runner_count;
    END IF;

    IF v_mds_count != v_runner_mds_count THEN
        RAISE EXCEPTION 'V4 validation failed: portfolio_mds_count=% != runner_mds_count=%',
            v_mds_count, v_runner_mds_count;
    END IF;
END $$;
