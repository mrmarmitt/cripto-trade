package com.marmitt.core.enums;

/**
 * Ciclo de vida do StrategyRunner.
 * Transições: CREATED → INITIALIZING → ACTIVE → HALTED → TERMINATING → ARCHIVED
 */
public enum RunnerStatus {

    /**
     * Runner criado mas ainda não inicializado
     */
    CREATED,

    /**
     * Runner em processo de inicialização (Boot Sequence)
     */
    INITIALIZING,

    /**
     * Runner operacional, processando sinais da estratégia
     */
    ACTIVE,

    /**
     * Runner pausado (Safe Mode ou intervenção manual)
     */
    HALTED,

    /**
     * Runner em processo de encerramento (aguardando ordens em voo)
     */
    TERMINATING,

    /**
     * Runner arquivado (soft delete — nunca removido fisicamente)
     */
    ARCHIVED;

    /**
     * Verifica se o Runner está operacional (pode receber sinais)
     */
    public boolean isOperational() {
        return this == ACTIVE;
    }

    /**
     * Verifica se o Runner está em estado terminal
     */
    public boolean isTerminal() {
        return this == TERMINATING || this == ARCHIVED;
    }
}
