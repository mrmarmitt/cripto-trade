-- T54: catalogo de estrategias remotas administrado por API (substitui ctrade.strategy.remote.catalog).
-- Nao ha exclusao: retirar de uso e status DISABLED. O token e da estrategia e nunca volta pela API.

CREATE TABLE remote_strategies (
    strategy_ref            UUID                     NOT NULL,
    name                    VARCHAR(200)             NOT NULL,
    version                 VARCHAR(100)             NOT NULL,
    base_url                VARCHAR(2000)            NOT NULL,
    token                   VARCHAR(2000)            NULL,
    status                  VARCHAR(20)              NOT NULL,
    verification            VARCHAR(20)              NOT NULL,
    last_error              TEXT                     NULL,
    last_verification_at    TIMESTAMP WITH TIME ZONE NULL,
    served_contract_version VARCHAR(50)              NULL,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT remote_strategies_pkey PRIMARY KEY (strategy_ref),
    CONSTRAINT chk_remote_strategies_status CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT chk_remote_strategies_verification CHECK (verification IN ('PENDING', 'VERIFIED', 'FAILED'))
);

-- Historico de mudancas e de resultados de conferencia, para o operador reconstruir o que aconteceu.
CREATE TABLE remote_strategy_events (
    id           UUID                     NOT NULL,
    -- Ordem de gravacao: eventos do mesmo instante (cadastro + conferencia) mantem a sequencia.
    seq          BIGINT GENERATED ALWAYS AS IDENTITY,
    strategy_ref UUID                     NOT NULL,
    type         VARCHAR(30)              NOT NULL,
    reason       TEXT                     NULL,
    detail       TEXT                     NULL,
    occurred_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT remote_strategy_events_pkey PRIMARY KEY (id),
    CONSTRAINT fk_remote_strategy_events_strategy FOREIGN KEY (strategy_ref) REFERENCES remote_strategies(strategy_ref)
);

CREATE INDEX idx_remote_strategy_events_strategy ON remote_strategy_events(strategy_ref, seq);
