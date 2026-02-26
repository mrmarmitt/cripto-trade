-- Enforce single OPEN position per runner+symbol
CREATE UNIQUE INDEX IF NOT EXISTS ux_positions_open_runner_symbol
    ON positions(runner_id, symbol)
    WHERE status = 'OPEN';
