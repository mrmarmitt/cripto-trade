package com.marmitt.core.ports.outbound;

import com.marmitt.core.domain.Portfolio;
import com.marmitt.core.domain.Position;
import com.marmitt.core.domain.Symbol;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface PortfolioPort {
    
    CompletableFuture<Portfolio> getCurrentPortfolio();
    
    CompletableFuture<Position> getPosition(Symbol symbol);
    
    CompletableFuture<List<Position>> getAllPositions();
    
    CompletableFuture<BigDecimal> getAvailableBalance();
    
    CompletableFuture<BigDecimal> getTotalBalance();
    
    CompletableFuture<Void> updatePosition(Position position);
    
    CompletableFuture<Boolean> hasPosition(Symbol symbol);
    
    CompletableFuture<Boolean> hasSufficientBalance(BigDecimal requiredAmount);
}