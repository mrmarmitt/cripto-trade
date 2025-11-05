package com.marmitt.core.domain.portfolio;

import com.marmitt.core.domain.Symbol;
import com.marmitt.core.enums.TransactionType;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Getter
@Builder
public class Transaction {
    
    private final UUID id;
    private final TransactionType type;
    private final Symbol symbol;
    private final Asset quantity;
    private final Asset price;
    private final Asset total;
    private final Asset fee;
    private final Instant timestamp;
    
    public Transaction(
            UUID id,
            TransactionType type,
            Symbol symbol,
            Asset quantity,
            Asset price,
            Asset total,
            Asset fee,
            Instant timestamp
    ) {
        this.id = Objects.requireNonNull(id, "Transaction ID cannot be null");
        this.type = Objects.requireNonNull(type, "Transaction type cannot be null");
        this.symbol = Objects.requireNonNull(symbol, "Symbol cannot be null");
        this.quantity = Objects.requireNonNull(quantity, "Quantity cannot be null");
        this.price = Objects.requireNonNull(price, "Price cannot be null");
        this.total = Objects.requireNonNull(total, "Total cannot be null");
        this.fee = Objects.requireNonNull(fee, "Fee cannot be null");
        this.timestamp = Objects.requireNonNull(timestamp, "Timestamp cannot be null");
        
        validateTransaction();
    }
    
    private void validateTransaction() {
        if (quantity.isPositive()) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        
        if (price.isPositive()) {
            throw new IllegalArgumentException("Price must be positive");
        }
        
        if (total.isPositive()) {
            throw new IllegalArgumentException("Total must be positive");
        }
    }

    public boolean isBuy() {
        return type == TransactionType.BUY;
    }
    
    public boolean isSell() {
        return type == TransactionType.SELL;
    }
}