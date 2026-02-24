package com.marmitt.core.application.usecase.portfolio;

import com.marmitt.core.dto.portfolio.PortfolioDto;
import com.marmitt.core.dto.portfolio.TransactionDto;
import com.marmitt.core.domain.portfolio.Portfolio;
import com.marmitt.core.ports.inbound.portfolio.QueryPortfolioPort;
import com.marmitt.core.ports.outbound.repository.PortfolioRepositoryPort;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
public class QueryPortfolioUseCase implements QueryPortfolioPort {

    private final PortfolioRepositoryPort portfolioRepository;

    public QueryPortfolioUseCase(PortfolioRepositoryPort portfolioRepository) {
        this.portfolioRepository = portfolioRepository;
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
                .collect(Collectors.toList());
    }

    @Override
    public List<TransactionDto> findTransactionsByPortfolioId(UUID portfolioId) {
        log.debug("Querying transactions for portfolio: {}", portfolioId);
        Optional<Portfolio> portfolioOpt = portfolioRepository.findById(portfolioId);

        if (portfolioOpt.isEmpty()) {
            log.warn("Portfolio not found with ID: {}", portfolioId);
            return Collections.emptyList();
        }

        return portfolioOpt.get().getTransactions().stream()
                .map(TransactionDto::fromDomain)
                .collect(Collectors.toList());
    }
}
