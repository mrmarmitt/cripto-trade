package com.marmitt.core.dto.boot;

import com.marmitt.core.enums.BootAccountQueryPolicy;
import com.marmitt.core.enums.BootFailureMode;

public record BootExecutionCommand(
        boolean phase1Enabled,
        boolean phase2Enabled,
        boolean phase3Enabled,
        boolean sanityEnabled,
        boolean zombieEnabled,
        boolean cutoffEnabled,
        BootFailureMode failureMode,
        BootAccountQueryPolicy accountQueryPolicy
) {
}
