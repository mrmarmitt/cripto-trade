package com.marmitt.core.ports.inbound.portfolio;

import com.marmitt.core.dto.portfolio.response.PortfolioDto;
import com.marmitt.core.dto.portfolio.response.GlobalBalanceDto;
import com.marmitt.core.dto.portfolio.response.PortfolioPnlDto;
import com.marmitt.core.dto.portfolio.response.TransactionDto;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QueryPortfolioPort {

    Optional<PortfolioDto> findById(UUID portfolioId);

    Optional<PortfolioDto> findByName(String name);

    List<PortfolioDto> findBySymbol(String symbol);

    List<TransactionDto> findTransactionsByPortfolioId(UUID portfolioId);

    Optional<GlobalBalanceDto> findBalanceByPortfolioId(UUID portfolioId);

    Optional<PortfolioPnlDto> findPnlByPortfolioId(UUID portfolioId);
}

