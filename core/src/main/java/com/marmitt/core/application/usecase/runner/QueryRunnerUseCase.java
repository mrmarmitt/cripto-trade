package com.marmitt.core.application.usecase.runner;

import com.marmitt.core.dto.portfolio.response.TransactionDto;
import com.marmitt.core.dto.runner.response.RunnerDto;
import com.marmitt.core.enums.TransactionStatus;
import com.marmitt.core.ports.inbound.runner.QueryRunnerPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
public class QueryRunnerUseCase implements QueryRunnerPort {

    private static final List<TransactionStatus> ALL_ACTIVE_STATUSES = List.of(
            TransactionStatus.PENDING,
            TransactionStatus.SUBMITTED,
            TransactionStatus.PARTIAL,
            TransactionStatus.FILLED,
            TransactionStatus.CANCELED,
            TransactionStatus.REJECTED,
            TransactionStatus.EXPIRED
    );

    private final StrategyRunnerRepositoryPort strategyRunnerRepository;

    public QueryRunnerUseCase(StrategyRunnerRepositoryPort strategyRunnerRepository) {
        this.strategyRunnerRepository = strategyRunnerRepository;
    }

    @Override
    public List<RunnerDto> findAll() {
        return strategyRunnerRepository.findAll().stream()
                .map(RunnerDto::fromDomain)
                .toList();
    }

    @Override
    public List<RunnerDto> findByPortfolioId(UUID portfolioId) {
        log.debug("Querying runners by portfolioId: {}", portfolioId);
        return strategyRunnerRepository.findByPortfolioId(portfolioId).stream()
                .map(RunnerDto::fromDomain)
                .toList();
    }

    @Override
    public List<TransactionDto> findTransactionsByRunnerId(UUID runnerId) {
        log.debug("Querying transactions for runner: {}", runnerId);
        if (strategyRunnerRepository.findById(runnerId).isEmpty()) {
            log.warn("Runner not found with ID: {}", runnerId);
            return Collections.emptyList();
        }
        return strategyRunnerRepository.findByRunnerIdAndStatuses(runnerId, ALL_ACTIVE_STATUSES).stream()
                .map(TransactionDto::fromDomain)
                .toList();
    }
}


