package com.marmitt.core.enums;

public enum TradingAction {
    SHOULD_BUY,
    SHOULD_SELL,
    SHOULD_HOLD,
    /** Pedido da estratégia para cancelar uma ordem em trânsito (alvo em StrategyOutputDto.targetTransactionId). */
    SHOULD_CANCEL
}