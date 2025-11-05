package com.marmitt.core.domain.portfolio;

import com.marmitt.core.domain.Symbol;
import com.marmitt.core.domain.value.Asset;
import lombok.Getter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public class Portfolio {
    
    private final UUID id;
    private final String name;
    private final UUID strategyId;
    private final String strategyName;
    private final Balance balance;
    private final Map<Symbol, Position> positions;
    private final List<Transaction> transactions;
    private boolean isActive;
    private final Instant createdAt;
    
    public Portfolio(
            UUID id,
            String name,
            UUID strategyId,
            String strategyName,
            Asset initialCapital
    ) {
        this.id = id;
        this.name = name;
        this.strategyId = strategyId;
        this.strategyName = strategyName;
        this.balance = Balance.withInitialCapital(initialCapital);
        this.positions = new ConcurrentHashMap<>();
        this.transactions = new ArrayList<>();
        this.isActive = true;
        this.createdAt = Instant.now();
    }

    public void executeBuy(Symbol symbol, Asset quantity, Asset price, Asset fee) {
        validateTradeParameters(symbol, quantity, price, fee);
        
        Asset total = quantity.multiply(price.amount());
        Asset totalWithFee = total.add(fee);
        
        if (balance.hasAvailableAmount(totalWithFee)) {
            throw new IllegalArgumentException("Insufficient balance for purchase");
        }
        
        // Update balance
        balance.allocate(totalWithFee);
        
        // Update or create position
        Position existingPosition = positions.get(symbol);
        if (existingPosition != null) {
            existingPosition.updatePosition(quantity, price);
        } else {
            positions.put(symbol, Position.create(symbol, quantity, price));
        }
        
        // Record transaction
        Transaction transaction = Transaction.builder()
                .type(com.marmitt.core.enums.TransactionType.BUY)
                .symbol(symbol)
                .quantity(quantity)
                .price(price)
                .total(total)
                .fee(fee)
                .build();
        
        transactions.add(transaction);
    }
    
    public void executeSell(Symbol symbol, Asset quantity, Asset price, Asset fee) {
        validateTradeParameters(symbol, quantity, price, fee);
        
        Position position = positions.get(symbol);
        if (position == null) {
            throw new IllegalArgumentException("No position found for symbol: " + symbol.value());
        }
        
        if (!position.canSell(quantity)) {
            throw new IllegalArgumentException("Insufficient quantity to sell");
        }
        
        Asset total = quantity.multiply(price.amount());
        Asset totalMinusFee = total.subtract(fee);
        
        // Update position
        position.reducePosition(quantity);
        if (position.isEmpty()) {
            positions.remove(symbol);
        }
        
        // Update balance - get back the proceeds minus fee
        balance.deallocate(total);
        
        // Record transaction
        Transaction transaction = Transaction.builder()
                .type(com.marmitt.core.enums.TransactionType.SELL)
                .symbol(symbol)
                .quantity(quantity)
                .price(price)
                .total(total)
                .fee(fee)
                .build();
        
        transactions.add(transaction);
    }
    
    public void updatePositionPrice(Symbol symbol, Asset newPrice) {
        Position position = positions.get(symbol);
        if (position != null) {
            position.updateCurrentPrice(newPrice);
        }
    }
    
    private void validateTradeParameters(Symbol symbol, Asset quantity, Asset price, Asset fee) {
        Objects.requireNonNull(symbol, "Symbol cannot be null");
        Objects.requireNonNull(quantity, "Quantity cannot be null");
        Objects.requireNonNull(price, "Price cannot be null");
        Objects.requireNonNull(fee, "Fee cannot be null");
        
        if (!isActive) {
            throw new IllegalStateException("Portfolio is not active");
        }
        
        if (quantity.isPositive()) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        
        if (price.isPositive()) {
            throw new IllegalArgumentException("Price must be positive");
        }
    }

    public void activate() {
        this.isActive = true;
    }

    public void deactivate() {
        this.isActive = false;
    }

}