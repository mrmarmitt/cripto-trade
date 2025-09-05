package com.marmitt.core.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record Portfolio(
        String accountId,
        BigDecimal totalBalance,
        BigDecimal availableBalance,
        BigDecimal lockedBalance,
        List<Position> positions,
        Instant lastUpdated
) {
    public BigDecimal getTotalValue() {
        return positions.stream()
                .map(Position::getCurrentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .add(availableBalance);
    }
    
    public Position getPosition(Symbol symbol) {
        return positions.stream()
                .filter(pos -> pos.symbol().equals(symbol))
                .findFirst()
                .orElse(null);
    }
    
    public boolean hasPosition(Symbol symbol) {
        return getPosition(symbol) != null;
    }
    
    public BigDecimal getPositionQuantity(Symbol symbol) {
        Position position = getPosition(symbol);
        return position != null ? position.quantity() : BigDecimal.ZERO;
    }
    
    public boolean hasSufficientBalance(BigDecimal requiredAmount) {
        return availableBalance.compareTo(requiredAmount) >= 0;
    }
    
    public BigDecimal getProfitLoss() {
        return positions.stream()
                .map(Position::getUnrealizedPnL)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}