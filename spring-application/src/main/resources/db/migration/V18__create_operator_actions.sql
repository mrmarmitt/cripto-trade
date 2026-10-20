-- T55: log unico das acoes do operador pela API (todo comando que muda estado: POST/PUT/PATCH/DELETE).
-- Responde "o que foi feito, por quem, por que e com que resultado" numa consulta. Inclui recusas.

CREATE TABLE operator_actions (
    id          UUID                     NOT NULL,
    seq         BIGINT GENERATED ALWAYS AS IDENTITY,
    actor       VARCHAR(200)             NOT NULL,
    method      VARCHAR(10)              NOT NULL,
    path        VARCHAR(2000)            NOT NULL,
    operation   VARCHAR(200)             NULL,
    reason      TEXT                     NULL,
    http_status INTEGER                  NOT NULL,
    error_code  VARCHAR(100)             NULL,
    duration_ms BIGINT                   NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT operator_actions_pkey PRIMARY KEY (id)
);

CREATE INDEX idx_operator_actions_occurred_at ON operator_actions(occurred_at);
