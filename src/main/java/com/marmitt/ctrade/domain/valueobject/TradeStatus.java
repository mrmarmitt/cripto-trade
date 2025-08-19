package com.marmitt.ctrade.domain.valueobject;

public enum TradeStatus {
    /**
     * Posição aberta - ainda não foi fechada
     */
    OPEN,
    
    /**
     * Posição fechada completamente
     */
    CLOSED,
    
    /**
     * Posição parcialmente fechada
     */
    PARTIAL_CLOSED
}