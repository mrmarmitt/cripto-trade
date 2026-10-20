-- Limpeza completa dos dados da aplicacao para recomecar uma rodada de testnet (T53).
-- Dado de testnet e descartavel; NUNCA rodar contra producao.
-- Mantem o schema e o historico do Flyway (flyway_schema_history): a aplicacao sobe sem migrar de novo.
-- Rodar com a aplicacao PARADA: ela guarda estado em memoria (estrategias, conexoes, ultimo preco).
BEGIN;

TRUNCATE TABLE
    operator_actions,
    remote_strategy_events,
    remote_strategies,
    safe_mode_changes,
    equity_snapshots,
    capital_event_ledger,
    dead_letter_entries,
    transaction_matches,
    positions,
    transactions,
    runner_market_data_sources,
    strategy_runners,
    margin_accounts,
    dust_accounts,
    global_balances,
    portfolios
    CASCADE;

COMMIT;
