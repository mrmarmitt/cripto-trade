package com.marmitt.core.dto.boot;

import com.marmitt.core.enums.BootAccountQueryPolicy;
import com.marmitt.core.enums.BootFailureMode;

import java.math.BigDecimal;

public record BootExecutionCommand(
        boolean phase1Enabled,
        boolean phase2Enabled,
        boolean phase3Enabled,
        boolean sanityEnabled,
        BigDecimal sanityThreshold,
        boolean zombieEnabled,
        boolean cutoffEnabled,
        boolean ttlEnabled,
        long ttlMs,
        BootFailureMode failureMode,
        BootAccountQueryPolicy accountQueryPolicy
) {
}
