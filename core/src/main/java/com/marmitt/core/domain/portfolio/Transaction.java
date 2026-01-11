package com.marmitt.core.domain.portfolio;

import com.marmitt.core.domain.Symbol;
import com.marmitt.core.enums.TransactionStatus;
import com.marmitt.core.enums.TransactionType;
import lombok.Builder;
import lombok.Getter;
import lombok.With;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Representa uma transação (ordem) no portfolio.
 * Registra todo o ciclo de vida desde a criação até a execução/rejeição.
 */
@Getter
@Builder
@With
public class Transaction {

    private final UUID id;

    /**
     * ID único para correlação com a exchange
     * Usado para identificar a resposta assíncrona da ordem
     */
    private final String clientOrderId;

    /**
     * Status atual da transação
     */
    private final TransactionStatus status;

    /**
     * Tipo da transação (BUY ou SELL)
     */
    private final TransactionType type;

    /**
     * Símbolo da transação
     */
    private final Symbol symbol;

    /**
     * Quantidade solicitada
     */
    private final Asset quantity;

    /**
     * Quantidade realmente executada (pode ser diferente da solicitada)
     * Null se transação ainda não foi executada
     */
    private final Asset executedQuantity;

    /**
     * Preço solicitado
     */
    private final Asset price;

    /**
     * Preço realmente executado (pode ser diferente do solicitado em MARKET orders)
     * Null se transação ainda não foi executada
     */
    private final Asset executedPrice;

    /**
     * Total estimado (quantity * price)
     */
    private final Asset total;

    /**
     * Taxa de execução
     * Para ordens PENDING/SUBMITTED é uma estimativa
     * Para ordens FILLED é o valor real cobrado
     */
    private final Asset fee;

    /**
     * Timestamp de quando a transação foi solicitada/criada
     */
    private final Instant requestedAt;

    /**
     * Timestamp de quando a transação foi executada
     * Null se ainda não foi executada
     */
    private final Instant executedAt;

    /**
     * Motivo da rejeição (se status == REJECTED)
     * Null caso contrário
     */
    private final String rejectReason;

    public Transaction(
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
            String rejectReason
    ) {
        this.id = Objects.requireNonNull(id, "Transaction ID cannot be null");
        this.clientOrderId = clientOrderId; // Pode ser null para transactions antigas
        this.status = Objects.requireNonNull(status, "Transaction status cannot be null");
        this.type = Objects.requireNonNull(type, "Transaction type cannot be null");
        this.symbol = Objects.requireNonNull(symbol, "Symbol cannot be null");
        this.quantity = Objects.requireNonNull(quantity, "Quantity cannot be null");
        this.executedQuantity = executedQuantity; // Null até ser executada
        this.price = Objects.requireNonNull(price, "Price cannot be null");
        this.executedPrice = executedPrice; // Null até ser executada
        this.total = Objects.requireNonNull(total, "Total cannot be null");
        this.fee = Objects.requireNonNull(fee, "Fee cannot be null");
        this.requestedAt = Objects.requireNonNull(requestedAt, "RequestedAt cannot be null");
        this.executedAt = executedAt; // Null até ser executada
        this.rejectReason = rejectReason; // Null exceto se REJECTED

        validateTransaction();
    }

    private void validateTransaction() {
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
        return executedQuantity != null ? executedQuantity : Asset.of(java.math.BigDecimal.ZERO, quantity.currency());
    }

    /**
     * Retorna preço efetivamente executado ou solicitado se não executada
     */
    public Asset getEffectivePrice() {
        return executedPrice != null ? executedPrice : price;
    }
}
