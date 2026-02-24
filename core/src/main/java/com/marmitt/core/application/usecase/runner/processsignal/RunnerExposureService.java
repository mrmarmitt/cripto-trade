package com.marmitt.core.application.usecase.runner.processsignal;

import com.marmitt.core.domain.runner.Position;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.enums.TransactionStatus;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

class RunnerExposureService {

    private final StrategyRunnerRepositoryPort strategyRunnerRepository;

    public RunnerExposureService(StrategyRunnerRepositoryPort strategyRunnerRepository) {
        this.strategyRunnerRepository = strategyRunnerRepository;
    }

    public BigDecimal calculateInFlightExposure(UUID runnerId) {
        List<Transaction> inflight = strategyRunnerRepository.findByRunnerIdAndStatuses(runnerId, List.of(
                TransactionStatus.PENDING,
                TransactionStatus.SUBMITTED,
                TransactionStatus.PARTIAL
        ));
        return inflight.stream()
                .map(Transaction::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public boolean hasOpenPositionOrInflight(StrategyRunner runner) {
        List<Position> openPositions = strategyRunnerRepository.findOpenPositionsByRunnerId(runner.getId());
        if (!openPositions.isEmpty()) {
            return true;
        }

        List<Transaction> inFlight = strategyRunnerRepository.findByRunnerIdAndStatuses(runner.getId(), List.of(
                TransactionStatus.PENDING,
                TransactionStatus.SUBMITTED,
                TransactionStatus.PARTIAL
        ));
        return !inFlight.isEmpty();
    }
}
