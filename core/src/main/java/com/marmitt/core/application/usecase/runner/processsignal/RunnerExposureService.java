package com.marmitt.core.application.usecase.runner.processsignal;

import com.marmitt.core.domain.runner.Position;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.enums.TransactionStatus;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Servico de leitura de exposicao operacional do StrategyRunner.
 * Encapsula consultas de posicoes e transacoes em voo.
 */
class RunnerExposureService {

    private final StrategyRunnerRepositoryPort strategyRunnerRepository;

    public RunnerExposureService(StrategyRunnerRepositoryPort strategyRunnerRepository) {
        this.strategyRunnerRepository = strategyRunnerRepository;
    }

    /**
     * Soma o valor total de transacoes em voo (PENDING/SUBMITTED/PARTIAL)
     * para o runner informado.
     */
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

    /**
     * Indica se o runner possui posicao aberta ou ordens em voo.
     * Usado pela policy de execucao SINGLE antes de aceitar novo BUY.
     */
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
