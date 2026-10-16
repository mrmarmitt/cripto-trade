-- T43: amostras de equity por portfolio para o monitor de drawdown (pico da janela de 24h
-- sobrevive a restart). Amostras com mais de 24h sao removidas pelo proprio monitor.

CREATE TABLE equity_snapshots (
    portfolio_id UUID                     NOT NULL,
    sampled_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    equity       NUMERIC(30, 8)           NOT NULL,
    CONSTRAINT equity_snapshots_pkey         PRIMARY KEY (portfolio_id, sampled_at),
    CONSTRAINT fk_equity_snapshots_portfolio FOREIGN KEY (portfolio_id) REFERENCES portfolios(id)
);

CREATE INDEX idx_equity_snapshots_sampled_at ON equity_snapshots(sampled_at);
