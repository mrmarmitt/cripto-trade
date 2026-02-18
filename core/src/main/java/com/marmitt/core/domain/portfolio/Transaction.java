package com.marmitt.core.domain.portfolio;

import com.marmitt.core.domain.Symbol;
import com.marmitt.core.enums.TransactionStatus;
import com.marmitt.core.enums.TransactionType;
import lombok.Builder;
import lombok.With;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * @deprecated Substituído por {@link com.marmitt.core.domain.runner.Transaction} no modelo alvo.
 *             A nova Transaction é FK para StrategyRunner (não Portfolio), usa BigDecimal em vez de Asset,
 *             adiciona exchangeOrderId, confidence, reasoning e version, e remove o campo fee
 *             (fee migra para TransactionMatch).
 *             Mantida temporariamente para compatibilidade durante a migração (F1-08).
 */
@Deprecated(forRemoval = true)
@Builder
@With
public record Transaction(
        UUID id,
        String clientOrderId,
        TransactionStatus status,
        TransactionType type,
        Symbol symbol,
        Asset quantity,
        Asset executedQuantity,
        Asset price,
        Asset executedPrice,
        Asset total,
        Asset fee,
        Instant requestedAt,
        Instant executedAt,
        String rejectReason,
        UUID targetLotId
) {
    /**
     * Construtor compacto com validações
     */
    public Transaction {
        Objects.requireNonNull(id, "Transaction ID cannot be null");
        // clientOrderId pode ser null para transactions antigas
        Objects.requireNonNull(status, "Transaction status cannot be null");
        Objects.requireNonNull(type, "Transaction type cannot be null");
        Objects.requireNonNull(symbol, "Symbol cannot be null");
        Objects.requireNonNull(quantity, "Quantity cannot be null");
        // executedQuantity pode ser null até ser executada
        Objects.requireNonNull(price, "Price cannot be null");
        // executedPrice pode ser null até ser executada
        Objects.requireNonNull(total, "Total cannot be null");
        Objects.requireNonNull(fee, "Fee cannot be null");
        Objects.requireNonNull(requestedAt, "RequestedAt cannot be null");
        // executedAt pode ser null até ser executada
        // rejectReason é null exceto se REJECTED

        validateTransaction(quantity, price, total, status, executedQuantity, executedPrice, executedAt, rejectReason);
    }

    private static void validateTransaction(
            Asset quantity,
            Asset price,
            Asset total,
            TransactionStatus status,
            Asset executedQuantity,
            Asset executedPrice,
            Instant executedAt,
            String rejectReason
    ) {
        if (!quantity.isPositive()) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        if (!price.isPositive()) {
            throw new IllegalArgumentException("Price must be positive");
        }

        if (!total.isPositive()) {
            throw new IllegalArgumentException("Total must be positive");
        }

        // Validações de consistência baseadas no status
        if (status.isExecuted()) {
            if (executedQuantity == null) {
                throw new IllegalArgumentException("Executed quantity required for FILLED/PARTIALLY_FILLED status");
            }
            if (executedPrice == null) {
                throw new IllegalArgumentException("Executed price required for FILLED/PARTIALLY_FILLED status");
            }
            if (executedAt == null) {
                throw new IllegalArgumentException("ExecutedAt timestamp required for FILLED/PARTIALLY_FILLED status");
            }
        }

        if (status == TransactionStatus.REJECTED && rejectReason == null) {
            throw new IllegalArgumentException("Reject reason required for REJECTED status");
        }
    }

    public boolean isBuy() {
        return type == TransactionType.BUY;
    }

    public boolean isSell() {
        return type == TransactionType.SELL;
    }

    /**
     * Verifica se a transação foi executada (total ou parcialmente)
     */
    public boolean isExecuted() {
        return status.isExecuted();
    }

    /**
     * Verifica se a transação está em estado final
     */
    public boolean isFinal() {
        return status.isFinal();
    }

    /**
     * Verifica se a transação falhou
     */
    public boolean isFailed() {
        return status.isFailed();
    }

    /**
     * Retorna quantidade efetivamente executada ou zero se não executada
     */
    public Asset getEffectiveQuantity() {
        return executedQuantity != null ? executedQuantity : Asset.of(BigDecimal.ZERO, quantity.currency());
    }

    /**
     * Retorna preço efetivamente executado ou solicitado se não executada
     */
    public Asset getEffectivePrice() {
        return executedPrice != null ? executedPrice : price;
    }
}
