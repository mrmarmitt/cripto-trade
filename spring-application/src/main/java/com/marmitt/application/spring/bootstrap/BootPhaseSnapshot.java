package com.marmitt.application.spring.bootstrap;

import java.time.Instant;

public record BootPhaseSnapshot(
        String phase,
        BootPhaseStatus status,
        Instant startedAt,
        Instant finishedAt,
        Long durationMs,
        String message
) {
}
