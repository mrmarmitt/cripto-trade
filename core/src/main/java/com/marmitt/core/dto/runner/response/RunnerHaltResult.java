package com.marmitt.core.dto.runner.response;

import com.marmitt.core.enums.RunnerStatus;

import java.util.UUID;

public record RunnerHaltResult(
        UUID id,
        String strategyName,
        String symbol,
        RunnerStatus previousStatus,
        RunnerStatus newStatus
) {}
