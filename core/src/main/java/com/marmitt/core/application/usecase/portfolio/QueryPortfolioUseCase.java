package com.marmitt.core.application.usecase.portfolio;

import com.marmitt.core.dto.portfolio.PortfolioDto;
import com.marmitt.core.dto.portfolio.TransactionDto;
import com.marmitt.core.enums.TransactionStatus;
import com.marmitt.core.ports.inbound.portfolio.QueryPortfolioPort;
import com.marmitt.core.ports.outbound.repository.PortfolioRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
public class QueryPortfolioUseCase implements QueryPortfolioPort {

    private static final List<TransactionStatus> ALL_ACTIVE_STATUSES = List.of(
            TransactionStatus.PENDING,
            TransactionStatus.SUBMITTED,
            TransactionStatus.PARTIAL,
            TransactionStatus.FILLED,
            TransactionStatus.CANCELED,
            TransactionStatus.REJECTED,
            TransactionStatus.EXPIRED
    );

    private final PortfolioRepositoryPort portfolioRepository;
    private final StrategyRunnerRepositoryPort strategyRunnerRepository;

    public QueryPortfolioUseCase(
            PortfolioRepositoryPort portfolioRepository,
            StrategyRunnerRepositoryPort strategyRunnerRepository
    ) {
        this.portfolioRepository = portfolioRepository;
        this.strategyRunnerRepository = strategyRunnerRepository;
    }

    @Override
    public Optional<PortfolioDto> findById(UUID portfolioId) {
        log.debug("Querying portfolio by ID: {}", portfolioId);
        return portfolioRepository.findById(portfolioId)
                .map(PortfolioDto::fromDomain);
    }

    @Override
    public Optional<PortfolioDto> findByName(String name) {
        log.debug("Querying portfolio by name: {}", name);
        return portfolioRepository.findByName(name)
                .map(PortfolioDto::fromDomain);
    }

    @Override
    public List<PortfolioDto> findBySymbol(String symbol) {
        log.debug("Querying portfolios by symbol: {}", symbol);
        return portfolioRepository.findBySymbol(symbol).stream()
                .map(PortfolioDto::fromDomain)
                .toList();
    }

    @Override
    public List<TransactionDto> findTransactionsByPortfolioId(UUID portfolioId) {
        log.debug("Querying transactions for portfolio: {}", portfolioId);

        if (portfolioRepository.findById(portfolioId).isEmpty()) {
            log.warn("Portfolio not found with ID: {}", portfolioId);
            return Collections.emptyList();
        }

        return strategyRunnerRepository.findByPortfolioId(portfolioId).stream()
                .flatMap(runner -> strategyRunnerRepository
                        .findByRunnerIdAndStatuses(runner.getId(), ALL_ACTIVE_STATUSES).stream())
                .map(TransactionDto::fromDomain)
                .toList();
    }
}
