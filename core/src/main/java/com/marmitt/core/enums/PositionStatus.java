package com.marmitt.core.enums;

/**
 * Estado da posição no ciclo de vida.
 * CLOSING indica que há ordens de venda em voo para esta posição.
 */
public enum PositionStatus {

    /**
     * Posição aberta com quantidade > 0
     */
    OPEN,

    /**
     * Posição com ordens de venda em voo (aguardando execução)
     */
    CLOSING,

    /**
     * Posição completamente fechada (quantity = 0)
     */
    CLOSED
}
