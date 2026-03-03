package com.marmitt.mock.config;

import java.util.Objects;

/**
 * Configuracao de readiness da exchange MOCK para testes de boot.
 */
public record MockBootReadinessConfig(
        boolean enabled,
        Mode mode,
        long simulatedDelayMs,
        String message
) {
    public MockBootReadinessConfig {
        Objects.requireNonNull(mode, "mode cannot be null");
        Objects.requireNonNull(message, "message cannot be null");
        if (simulatedDelayMs < 0) {
            throw new IllegalArgumentException("simulatedDelayMs cannot be negative");
        }
    }

    public static MockBootReadinessConfig defaultConfig() {
        return new MockBootReadinessConfig(
                true,
                Mode.READY,
                0L,
                "Mock readiness OK"
        );
    }

    public enum Mode {
        READY,
        CONNECTIVITY_FAIL,
        AUTH_FAIL,
        TIMEOUT
    }
}
