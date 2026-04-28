package com.marmitt.core.dto.boot;

import com.marmitt.core.enums.BootPhaseStatus;

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
