package com.marmitt.core.dto.exchange.boot;

import java.time.Instant;

/**
 * Resultado da verificacao de prontidao de uma exchange durante o boot.
 */
public record ExchangeBootReadiness(
        String exchangeName,
        boolean ready,
        String code,
        String message,
        Instant checkedAt
) {

    public static ExchangeBootReadiness ready(String exchangeName, String message) {
        return new ExchangeBootReadiness(exchangeName, true, "READY", message, Instant.now());
    }

    public static ExchangeBootReadiness notReady(String exchangeName, String code, String message) {
        return new ExchangeBootReadiness(exchangeName, false, code, message, Instant.now());
    }
}
