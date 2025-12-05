package com.marmitt.core.domain.portfolio;

import com.marmitt.core.domain.Symbol;
import com.marmitt.core.domain.strategy.PortfolioContext;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Getter
public class Portfolio {
    
    private final UUID id;
    private final String name;
    private final UUID strategyId;
    private final String strategyName;
    private final Symbol symbol;
    private final Balance balance;
    private Position position;
    private final List<Transaction> transactions;
    private boolean isActive;
    private final Instant createdAt;
    private Instant lastExecutionTime;
    
    // Configurações de limitação
    private static final BigDecimal MINIMUM_OPERATION_AMOUNT = new BigDecimal("10.00"); // $10 USD
    private static final Duration EXECUTION_COOLDOWN = Duration.ofSeconds(30); // 30 segundos entre execuções
    private static final BigDecimal DEFAULT_MAX_EXPOSURE_PERCENTAGE = new BigDecimal("0.20"); // 20% por símbolo
    
    public Portfolio(
            UUID id,
            String name,
            UUID strategyId,
            String strategyName,
            Symbol symbol,
            Asset initialCapital
    ) {
        this.id = id;
        this.name = name;
        this.strategyId = strategyId;
        this.strategyName = strategyName;
        this.symbol = Objects.requireNonNull(symbol, "Symbol cannot be null");
        this.balance = Balance.withInitialCapital(initialCapital);
        this.position = null; // No initial position
        this.transactions = new ArrayList<>();
        this.isActive = true;
        this.createdAt = Instant.now();
        this.lastExecutionTime = null;
    }

    public void executeBuy(Asset quantity, Asset price, Asset fee) {
        validateTradeParameters(quantity, price, fee);
        
        Asset total = quantity.multiply(price.amount());
        Asset totalWithFee = total.add(fee);
        
        if (balance.hasAvailableAmount(totalWithFee)) {
            throw new IllegalArgumentException("Insufficient balance for purchase");
        }
        
        // Update balance
        balance.allocate(totalWithFee);
        
        // Update or create position
        if (position != null) {
            position.updatePosition(quantity, price);
        } else {
            position = Position.create(this.symbol, quantity, price);
        }
        
        // Record transaction
        Transaction transaction = Transaction.builder()
                .type(com.marmitt.core.enums.TransactionType.BUY)
                .symbol(this.symbol)
                .quantity(quantity)
                .price(price)
                .total(total)
                .fee(fee)
                .build();
        
        transactions.add(transaction);
    }
    
    public void executeSell(Asset quantity, Asset price, Asset fee) {
        validateTradeParameters(quantity, price, fee);
        
        if (position == null) {
            throw new IllegalArgumentException("No position found for currency: " + this.symbol.value());
        }
        
        if (!position.canSell(quantity)) {
            throw new IllegalArgumentException("Insufficient quantity to sell");
        }
        
        Asset total = quantity.multiply(price.amount());
        Asset totalMinusFee = total.subtract(fee);
        
        // Update position
        position.reducePosition(quantity);
        if (position.isEmpty()) {
            position = null; // Clear empty position
        }
        
        // Update balance - get back the proceeds minus fee
        balance.deallocate(total);
        
        // Record transaction
        Transaction transaction = Transaction.builder()
                .type(com.marmitt.core.enums.TransactionType.SELL)
                .symbol(this.symbol)
                .quantity(quantity)
                .price(price)
                .total(total)
                .fee(fee)
                .build();
        
        transactions.add(transaction);
    }
    
    public void updatePositionPrice(Asset newPrice) {
        if (position != null) {
            position.updateCurrentPrice(newPrice);
        }
    }
    
    public boolean hasPosition() {
        return position != null && !position.isEmpty();
    }
    
    public Asset getCurrentPositionValue() {
        return hasPosition() ? position.getCurrentValue() : 
               Asset.fiat(BigDecimal.ZERO, balance.getAvailable().currency());
    }
    
    public BigDecimal getCurrentPositionQuantity() {
        return hasPosition() ? position.getQuantity().amount() : BigDecimal.ZERO;
    }
    
    private void validateTradeParameters(Asset quantity, Asset price, Asset fee) {
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
    
    /**
     * Verifica se o portfolio está elegível para execução de estratégia
     */
    public boolean isValid() {
        if (!this.isActive) {
            return false;
        }

        if (this.strategyId == null) {
            return false;
        }

        if (!hasMinimumCapitalForOperation()) {
            return false;
        }

        // 6. Deve respeitar cooldown entre execuções
        if (isInCooldownPeriod()) {
            return false;
        }

        return true;
    }
    
    private boolean hasMinimumCapitalForOperation() {
        return balance.getAvailable().amount().compareTo(MINIMUM_OPERATION_AMOUNT) >= 0;
    }

    private boolean isInCooldownPeriod() {
        if (lastExecutionTime == null) {
            return false; // Primeira execução
        }
        
        Duration timeSinceLastExecution = Duration.between(lastExecutionTime, Instant.now());
        return timeSinceLastExecution.compareTo(EXECUTION_COOLDOWN) < 0;
    }
    
    /**
     * Cria PortfolioContext para ser passado para a Strategy
     * Contém todos os dados necessários para que a Strategy possa calcular quantities
     */
    public PortfolioContext createContext() {
        return PortfolioContext.builder()
            .portfolioId(this.id)
            .portfolioName(this.name)
            .symbol(this.symbol)
            .totalCapital(this.balance.getTotal())
            .availableBalance(this.balance.getAvailable())
            .allocatedBalance(this.balance.getAllocated())
            .position(this.position) // Single position
            .minimumOperationAmount(MINIMUM_OPERATION_AMOUNT)
            .maxExposurePerSymbol(DEFAULT_MAX_EXPOSURE_PERCENTAGE)
            .build();
    }
    
    /**
     * Atualiza timestamp da última execução
     * Deve ser chamado após execução bem-sucedida da estratégia
     */
    public void updateLastExecutionTime() {
        this.lastExecutionTime = Instant.now();
    }
}