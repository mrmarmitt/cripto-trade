package com.marmitt.core.dto.boot;

import com.marmitt.core.application.usecase.runner.RunnerBootRecoveryUseCase;

import java.util.List;

public record BootExecutionSummary(
        int portfoliosCount,
        int eligibleRunnersCount,
        List<RunnerBootRecoveryUseCase.RecoverySummary> runnerSummaries
) {
}
