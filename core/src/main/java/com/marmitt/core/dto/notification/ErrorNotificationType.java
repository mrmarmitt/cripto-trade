package com.marmitt.core.dto.notification;

/**
 * Categoria de um erro terminal reportado ativamente ao operador.
 *
 * <ul>
 *   <li>{@link #DLQ_PERSISTED} — um evento esgotou os retries e foi persistido na dead letter queue.</li>
 *   <li>{@link #BOOT_FAIL_FAST} — o boot abortou em modo fail-fast.</li>
 * </ul>
 */
public enum ErrorNotificationType {
    DLQ_PERSISTED,
    BOOT_FAIL_FAST,
    /** Monitor de capital acionou o Safe Mode automaticamente (T43). */
    CIRCUIT_BREAKER_TRIGGERED,
    /** Protecao do lote vendeu a posicao sem a estrategia (T44). */
    PROTECTIVE_EXIT
}
