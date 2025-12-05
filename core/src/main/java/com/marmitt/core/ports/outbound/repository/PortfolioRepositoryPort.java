package com.marmitt.core.ports.outbound.repository;

import com.marmitt.core.domain.portfolio.Portfolio;
import com.marmitt.core.ports.outbound.strategy.TradingStrategy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PortfolioRepositoryPort {

    Optional<Portfolio> findById(UUID portfolioId);

    Optional<Portfolio> findByName(String portfolioName);

    /**
     * Busca portfolios por símbolo (model 1:1:1 permite múltiplos portfolios para mesmo símbolo,
     * mas cada portfolio gerencia apenas um símbolo)
     */
    List<Portfolio> findBySymbol(String symbol);
    
    /**
     * Busca portfolio específico por símbolo e estratégia (deve retornar no máximo 1)
     */
    Optional<Portfolio> findBySymbolAndStrategy(String symbol, UUID strategyId);

    void registerPortfolio(Portfolio portfolio);
    
    /**
     * Salva ou atualiza um portfolio
     * 
     * @param portfolio Portfolio a ser salvo
     * @return O portfolio salvo
     */
    Portfolio save(Portfolio portfolio);
}