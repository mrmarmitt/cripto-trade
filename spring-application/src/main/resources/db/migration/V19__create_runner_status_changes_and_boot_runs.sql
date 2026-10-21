-- T59: historico do que antes se perdia (transicoes do runner e execucoes do boot) e indices por instante
-- nas tabelas lidas pela linha do tempo de eventos (GET /api/admin/events).

CREATE TABLE runner_status_changes (
    id          UUID                     NOT NULL,
    runner_id   UUID                     NOT NULL,
    from_status VARCHAR(20)              NOT NULL,
    to_status   VARCHAR(20)              NOT NULL,
    origin      VARCHAR(20)              NOT NULL,
    reason      TEXT                     NOT NULL,
    changed_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT runner_status_changes_pkey      PRIMARY KEY (id),
    CONSTRAINT fk_runner_status_changes_runner FOREIGN KEY (runner_id) REFERENCES strategy_runners(id),
    CONSTRAINT chk_runner_status_changes_origin CHECK (origin IN ('MANUAL', 'AUTOMATIC', 'BOOT'))
);

CREATE INDEX idx_runner_status_changes_changed_at ON runner_status_changes(changed_at);
CREATE INDEX idx_runner_status_changes_runner     ON runner_status_changes(runner_id, changed_at);

CREATE TABLE boot_runs (
    run_id          VARCHAR(64)              NOT NULL,
    mode            VARCHAR(30)              NOT NULL,
    status          VARCHAR(20)              NOT NULL,
    started_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    failure_phase   VARCHAR(100)             NULL,
    failure_message TEXT                     NULL,
    CONSTRAINT boot_runs_pkey       PRIMARY KEY (run_id),
    CONSTRAINT chk_boot_runs_status CHECK (status IN ('SUCCESS', 'FAILED'))
);

CREATE INDEX idx_boot_runs_finished_at ON boot_runs(finished_at);

CREATE INDEX idx_safe_mode_changes_changed_at       ON safe_mode_changes(changed_at);
CREATE INDEX idx_dead_letter_entries_created_at     ON dead_letter_entries(created_at);
CREATE INDEX idx_remote_strategy_events_occurred_at ON remote_strategy_events(occurred_at);
