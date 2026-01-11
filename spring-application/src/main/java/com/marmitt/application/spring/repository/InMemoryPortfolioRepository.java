package com.marmitt.application.spring.repository;

import com.marmitt.core.domain.portfolio.Portfolio;
import com.marmitt.core.ports.outbound.repository.PortfolioRepositoryPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Repository
public class InMemoryPortfolioRepository implements PortfolioRepositoryPort {

    private final Map<UUID, Portfolio> portfolios = new ConcurrentHashMap<>();
    private final Map<String, UUID> portfoliosByName = new ConcurrentHashMap<>();

    @Override
    public Optional<Portfolio> findById(UUID portfolioId) {
        Portfolio portfolio = portfolios.get(portfolioId);
        return Optional.ofNullable(portfolio);
    }

    @Override
    public Optional<Portfolio> findByName(String portfolioName) {
        UUID portfolioId = portfoliosByName.get(portfolioName.toLowerCase());
        if (portfolioId == null) {
            return Optional.empty();
        }
        return findById(portfolioId);
    }

    @Override
    public List<Portfolio> findBySymbol(String symbol) {
        String normalizedSymbol = symbol.toUpperCase();
        List<Portfolio> result = portfolios.values().stream()
                .filter(portfolio -> portfolio.getSymbol().value().equalsIgnoreCase(normalizedSymbol))
                .collect(Collectors.toList());

        return result;
    }

    @Override
    public Optional<Portfolio> findBySymbolAndStrategy(String symbol, UUID strategyId) {
        String normalizedSymbol = symbol.toUpperCase();
        return portfolios.values().stream()
                .filter(portfolio -> portfolio.getSymbol().value().equalsIgnoreCase(normalizedSymbol))
                .filter(portfolio -> portfolio.getStrategyId().equals(strategyId))
                .findFirst();
    }

    @Override
    public List<Portfolio> findAll() {
        return List.copyOf(portfolios.values());
    }

    @Override
    public void registerPortfolio(Portfolio portfolio) {
        Objects.requireNonNull(portfolio, "Portfolio cannot be null");
        Objects.requireNonNull(portfolio.getId(), "Portfolio ID cannot be null");
        Objects.requireNonNull(portfolio.getName(), "Portfolio name cannot be null");

        UUID portfolioId = portfolio.getId();
        String portfolioName = portfolio.getName().toLowerCase();

        // Validar se já existe portfolio com mesmo nome
        if (portfoliosByName.containsKey(portfolioName)) {
            UUID existingId = portfoliosByName.get(portfolioName);
            if (!existingId.equals(portfolioId)) {
                throw new IllegalArgumentException(
                    "Portfolio with name '" + portfolio.getName() + "' already exists with different ID"
                );
            }
        }

        portfolios.put(portfolioId, portfolio);
        portfoliosByName.put(portfolioName, portfolioId);

        log.info("Portfolio registered - ID: {}, Name: {}, Symbol: {}, Strategy: {}",
                portfolioId,
                portfolio.getName(),
                portfolio.getSymbol().value(),
                portfolio.getStrategyName());
    }

    @Override
    public Portfolio save(Portfolio portfolio) {
        Objects.requireNonNull(portfolio, "Portfolio cannot be null");
        Objects.requireNonNull(portfolio.getId(), "Portfolio ID cannot be null");

        UUID portfolioId = portfolio.getId();

        if (!portfolios.containsKey(portfolioId)) {
            registerPortfolio(portfolio);
        } else {
            portfolios.put(portfolioId, portfolio);
        }

        return portfolio;
    }
}
