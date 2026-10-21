package com.marmitt.core.domain.runner;

import com.marmitt.core.enums.RunnerStatusOrigin;

import java.util.Objects;

/**
 * Origem e motivo de uma transicao do runner (T59). Toda transicao exige uma causa, para o historico
 * em {@link RunnerStatusChange} responder "quem mudou e por que".
 */
public record RunnerStatusCause(RunnerStatusOrigin origin, String reason) {

    public RunnerStatusCause {
        Objects.requireNonNull(origin, "origin cannot be null");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason cannot be blank");
        }
    }

    public static RunnerStatusCause manual(String reason) {
        return new RunnerStatusCause(RunnerStatusOrigin.MANUAL, reason);
    }

    public static RunnerStatusCause automatic(String reason) {
        return new RunnerStatusCause(RunnerStatusOrigin.AUTOMATIC, reason);
    }

    public static RunnerStatusCause boot(String reason) {
        return new RunnerStatusCause(RunnerStatusOrigin.BOOT, reason);
    }
}
