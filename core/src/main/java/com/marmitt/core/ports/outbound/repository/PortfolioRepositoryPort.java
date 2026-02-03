package com.marmitt.core.ports.outbound.repository;

import com.marmitt.core.domain.portfolio.Balance;
import com.marmitt.core.domain.portfolio.Portfolio;
import com.marmitt.core.domain.portfolio.Position;
import com.marmitt.core.domain.portfolio.Transaction;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PortfolioRepositoryPort {

    Optional<Portfolio> findById(UUID portfolioId);

    Optional<Portfolio> findByName(String portfolioName);

    List<Portfolio> findBySymbol(String symbol);
    
    Optional<Portfolio> findBySymbolAndStrategy(String symbol, UUID strategyId);

    List<Portfolio> findAll();

    void registerPortfolio(Portfolio portfolio);

    void saveBalance(UUID portfolioId, Balance balance, Instant lastExecutionTime);

    void saveTransaction(UUID portfolioId, Transaction transaction);

    void saveTradeExecution(UUID portfolioId, Balance balance, Instant lastExecutionTime, Position position, Transaction transaction);
}