package com.marmitt.core.dto.boot;

import com.marmitt.core.enums.BootRunStatus;

import java.time.Instant;
import java.util.Objects;

/**
 * Execucao do boot ja terminada, para o historico (T59). {@code failurePhase} e {@code failureMessage}
 * so existem quando {@code status} e {@code FAILED}; a fase fica nula se a falha veio de fora das fases.
 */
public record BootRun(
        String runId,
        String mode,
        BootRunStatus status,
        Instant startedAt,
        Instant finishedAt,
        String failurePhase,
        String failureMessage
) {
    public BootRun {
        Objects.requireNonNull(runId, "runId cannot be null");
        Objects.requireNonNull(mode, "mode cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        Objects.requireNonNull(startedAt, "startedAt cannot be null");
        Objects.requireNonNull(finishedAt, "finishedAt cannot be null");
    }
}
