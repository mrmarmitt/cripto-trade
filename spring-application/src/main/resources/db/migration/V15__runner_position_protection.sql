-- T44: protecao do lote independente da estrategia, por runner.
-- max_loss_percent_per_lot: fracao em (0, 1) do custo do lote; default 0.05 tambem para os runners
-- existentes (decisao de negocio: lotes ja abertos entram na protecao).
-- max_holding_seconds: idade maxima do lote; NULL desliga (default).

ALTER TABLE strategy_runners
    ADD COLUMN max_loss_percent_per_lot NUMERIC(5, 4) NOT NULL DEFAULT 0.05,
    ADD COLUMN max_holding_seconds      BIGINT        NULL;

ALTER TABLE strategy_runners
    ADD CONSTRAINT chk_strategy_runners_max_loss_percent_per_lot
        CHECK (max_loss_percent_per_lot > 0 AND max_loss_percent_per_lot < 1),
    ADD CONSTRAINT chk_strategy_runners_max_holding_seconds
        CHECK (max_holding_seconds IS NULL OR max_holding_seconds > 0);
