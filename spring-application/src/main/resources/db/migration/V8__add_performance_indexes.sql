-- ============================================================
-- V8: Performance indexes
-- Adds optimized partial and composite indexes for the new query
-- patterns introduced by the StrategyRunner aggregate.
-- IG Seção 3.8 (V8)
-- ============================================================

-- ============================================================
-- 1. Partial indexes for frequent hot-path queries
-- ============================================================

-- Open positions by runner (hot path: execution policy checks, signal routing)
CREATE INDEX idx_positions_runner_open
    ON positions(runner_id, status)
    WHERE status = 'OPEN';

-- In-flight transactions by runner (Boot Sequence Step 2 + Watchdog monitoring)
CREATE INDEX idx_transactions_inflight
    ON transactions(runner_id, status)
    WHERE status IN ('PENDING', 'SUBMITTED', 'PARTIAL');

-- Active runners: enforce at most one active runner per (strategy, symbol, exchange, portfolio)
CREATE UNIQUE INDEX idx_runners_active_unique
    ON strategy_runners(strategy_id, symbol, exchange_id, portfolio_id)
    WHERE status NOT IN ('ARCHIVED', 'TERMINATING');

-- Unresolved dust entries by portfolio (DustAccount reconciliation)
CREATE INDEX idx_dust_portfolio_unresolved
    ON dust_accounts(portfolio_id, is_resolved)
    WHERE is_resolved = FALSE;

-- Unresolved DLQ entries by portfolio (operator dashboard + alert queries)
CREATE INDEX idx_dlq_portfolio_unresolved
    ON dead_letter_entries(portfolio_id, is_resolved)
    WHERE is_resolved = FALSE;

-- ============================================================
-- 2. Composite indexes for JOIN patterns
-- ============================================================

-- Matches by runner (PnL aggregation, audit queries)
CREATE INDEX idx_matches_runner ON transaction_matches(runner_id);

-- Runners by portfolio (Portfolio callback routing, listing)
CREATE INDEX idx_runners_portfolio ON strategy_runners(portfolio_id);

-- Runners by status (operational runner queries)
CREATE INDEX idx_runners_status ON strategy_runners(status);
