package com.marmitt.core.enums;

/**
 * Tipos estaveis da linha do tempo de eventos (T59). Cada tipo vem de uma unica tabela de origem; a linha
 * do tempo so le, nao copia.
 */
public enum TimelineEventType {
    /** Mudanca de nivel do Safe Mode, manual ou automatica ({@code safe_mode_changes}). */
    SAFE_MODE_CHANGED,
    /** Transicao de estado do runner, com origem e motivo ({@code runner_status_changes}). */
    RUNNER_STATUS_CHANGED,
    /** Venda do lote pela protecao (T44): transacao SELL com o reasoning da protecao. */
    PROTECTIVE_EXIT,
    /** Entrada nova na DLQ ({@code dead_letter_entries}). */
    DLQ_ENTRY,
    /** Comando do operador pela API ({@code operator_actions}). */
    OPERATOR_ACTION,
    /** Mudanca ou conferencia no catalogo de estrategias ({@code remote_strategy_events}). */
    STRATEGY_CATALOG_CHANGED,
    /** Boot terminado com sucesso ({@code boot_runs}). */
    BOOT_COMPLETED,
    /** Boot que falhou ({@code boot_runs}). */
    BOOT_FAILED
}
