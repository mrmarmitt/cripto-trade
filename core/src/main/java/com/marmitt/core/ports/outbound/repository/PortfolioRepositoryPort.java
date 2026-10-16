package com.marmitt.core.ports.outbound.repository;

import com.marmitt.core.domain.portfolio.Portfolio;
import com.marmitt.core.domain.portfolio.SafeModeChange;

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

    /**
     * Persiste o novo nivel do Safe Mode e o registro de auditoria na mesma transacao (T45).
     * Usa o {@code version} lido do portfolio como controle otimista.
     *
     * @throws com.marmitt.core.exceptions.SafeModeTransitionException se outro processo mudou
     *         o portfolio depois da leitura
     */
    void saveSafeModeChange(Portfolio portfolio, SafeModeChange change);
}