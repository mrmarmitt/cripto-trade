package com.marmitt.core.domain.runner;

import com.marmitt.core.enums.RunnerStatus;
import com.marmitt.core.enums.RunnerStatusOrigin;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Registro de auditoria de uma transicao de estado do runner (T59). Gravado junto com o runner, na
 * mesma transacao, por {@code StrategyRunnerRepositoryPort.save}.
 */
public record RunnerStatusChange(
        UUID id,
        UUID runnerId,
        RunnerStatus from,
        RunnerStatus to,
        RunnerStatusOrigin origin,
        String reason,
        Instant changedAt
) {
    public RunnerStatusChange {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(runnerId, "runnerId cannot be null");
        Objects.requireNonNull(from, "from cannot be null");
        Objects.requireNonNull(to, "to cannot be null");
        Objects.requireNonNull(origin, "origin cannot be null");
        Objects.requireNonNull(reason, "reason cannot be null");
        Objects.requireNonNull(changedAt, "changedAt cannot be null");
    }
}
