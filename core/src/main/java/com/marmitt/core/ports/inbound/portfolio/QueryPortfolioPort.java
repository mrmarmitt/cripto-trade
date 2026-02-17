package com.marmitt.core.ports.inbound.portfolio;

import com.marmitt.core.dto.portfolio.PortfolioDto;
import com.marmitt.core.dto.portfolio.TransactionDto;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QueryPortfolioPort {

    Optional<PortfolioDto> findById(UUID portfolioId);

    Optional<PortfolioDto> findByName(String name);

    List<PortfolioDto> findBySymbol(String symbol);

    List<TransactionDto> findTransactionsByPortfolioId(UUID portfolioId);
}
