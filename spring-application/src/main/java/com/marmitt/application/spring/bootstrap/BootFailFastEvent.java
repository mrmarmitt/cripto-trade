package com.marmitt.application.spring.bootstrap;

import java.time.Instant;

public record BootFailFastEvent(
        String runId,
        String phase,
        String code,
        String message,
        Instant timestamp
) {
}
