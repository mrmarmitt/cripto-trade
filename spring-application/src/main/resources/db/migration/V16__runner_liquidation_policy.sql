-- T48: politica de liquidacao do Safe Mode PANIC_SELL, por runner.
-- liquidation_order_type: MARKET (default, comportamento da T45) ou LIMIT.
-- liquidation_max_slippage_percent: afastamento da SELL LIMIT abaixo do ultimo preco, fracao em (0, 1);
-- default 0.01 (decisao de negocio).
-- liquidation_escalation_ticks: ticks sem preenchimento antes de cancelar e escalar para MARKET; default 3.

ALTER TABLE strategy_runners
    ADD COLUMN liquidation_order_type           VARCHAR(20)   NOT NULL DEFAULT 'MARKET',
    ADD COLUMN liquidation_max_slippage_percent NUMERIC(5, 4) NOT NULL DEFAULT 0.01,
    ADD COLUMN liquidation_escalation_ticks     INTEGER       NOT NULL DEFAULT 3;

ALTER TABLE strategy_runners
    ADD CONSTRAINT chk_strategy_runners_liquidation_order_type
        CHECK (liquidation_order_type IN ('MARKET', 'LIMIT')),
    ADD CONSTRAINT chk_strategy_runners_liquidation_max_slippage_percent
        CHECK (liquidation_max_slippage_percent > 0 AND liquidation_max_slippage_percent < 1),
    ADD CONSTRAINT chk_strategy_runners_liquidation_escalation_ticks
        CHECK (liquidation_escalation_ticks >= 1);
