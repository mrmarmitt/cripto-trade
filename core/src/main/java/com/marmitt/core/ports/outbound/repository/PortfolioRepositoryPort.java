package com.marmitt.core.ports.outbound.repository;

import com.marmitt.core.domain.portfolio.Portfolio;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PortfolioRepositoryPort {

    Optional<Portfolio> findById(UUID portfolioId);

    Optional<Portfolio> findByName(String portfolioName);

    /** Returns portfolios that have at least one operational runner for the given symbol. */
    List<Portfolio> findBySymbol(String symbol);

    List<Portfolio> findAll();

    void registerPortfolio(Portfolio portfolio);
}