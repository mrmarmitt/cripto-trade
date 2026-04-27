package com.marmitt.core.dto.boot;

import com.marmitt.core.enums.BootAccountQueryPolicy;
import com.marmitt.core.enums.BootFailureMode;

import java.math.BigDecimal;

public record BootExecutionCommand(
        boolean phase1Enabled,
        boolean phase2Enabled,
        boolean phase3Enabled,
        BootFailureMode phase2Mode,
        BootAccountQueryPolicy accountQueryPolicy,
        boolean sanityEnabled,
        BigDecimal sanityThreshold,
        boolean zombieDetectionEnabled,
        boolean reservationTtlEnabled,
        long reservationTtlMs,
        boolean cutoffEnabled
) {
}
