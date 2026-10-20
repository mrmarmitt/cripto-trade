package com.marmitt.core.dto.operations;

import java.time.Duration;
import java.util.Objects;

/**
 * Pedido de comparacao do registro local com a exchange no estado consolidado (T55-C2). Consome cota da
 * API da exchange, entao so roda quando pedido.
 *
 * @param tradeWindow janela de trades conferida, terminando agora
 */
public record ExchangeComparison(boolean enabled, Duration tradeWindow) {

    public static final ExchangeComparison NONE = new ExchangeComparison(false, Duration.ZERO);

    public ExchangeComparison {
        Objects.requireNonNull(tradeWindow, "tradeWindow cannot be null");
        if (enabled && (tradeWindow.isZero() || tradeWindow.isNegative())) {
            throw new IllegalArgumentException("tradeWindow must be positive: " + tradeWindow);
        }
    }

    public static ExchangeComparison over(Duration tradeWindow) {
        return new ExchangeComparison(true, tradeWindow);
    }
}
