package com.marmitt.application.spring.bootstrap;

import java.time.Instant;
import java.util.List;

public record BootRunSnapshot(
        String runId,
        BootRunStatus status,
        String mode,
        Instant startedAt,
        Instant finishedAt,
        Long durationMs,
        String failurePhase,
        String failureMessage,
        List<BootPhaseSnapshot> phases
) {
}
