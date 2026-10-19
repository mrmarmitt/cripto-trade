package com.marmitt.core.dto.runner.response;

import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.enums.RunnerStatus;

import java.util.UUID;

public record RunnerTransitionResult(
        UUID id,
        String strategyName,
        String symbol,
        RunnerStatus previousStatus,
        RunnerStatus newStatus
) {

    /** Resultado de uma transicao do runner: status anterior e o atual (halt, start, resume, terminate). */
    public static RunnerTransitionResult of(StrategyRunner runner, RunnerStatus previousStatus) {
        return new RunnerTransitionResult(runner.getId(), runner.getStrategyName(), runner.getSymbol(),
                previousStatus, runner.getStatus());
    }
}
