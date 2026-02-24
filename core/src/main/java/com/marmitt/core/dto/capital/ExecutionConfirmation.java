package com.marmitt.core.dto.capital;

import com.marmitt.core.domain.shared.Fee;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Payload da notificação de execução enviada pelo Runner ao Portfolio após cada match.
 * Entrada do método assíncrono {@code CapitalManager.confirmExecution()}.
 * <p>
 * Publicado após cada {@code TransactionMatch} ser persistido (PARTIAL ou FILLED).
 * O Portfolio usa {@code matchId} para garantir idempotência — duplicatas são descartadas.
 * <p>
 * Efeito contábil: {@code reserved -= totalCost}, {@code realized += pnlAmount},
 * {@code totalFeesPaid += fee.convertedAmount()}.
 *
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 5.2.2</a>
 */
public record ExecutionConfirmation(
        UUID transactionId,
        UUID runnerId,
        UUID matchId,
        BigDecimal executedQuantity,
        BigDecimal executedPrice,
        Fee fee,
        BigDecimal totalCost,
        boolean isFinal
) {
    public ExecutionConfirmation {
        Objects.requireNonNull(transactionId, "transactionId cannot be null");
        Objects.requireNonNull(runnerId, "runnerId cannot be null");
        Objects.requireNonNull(matchId, "matchId cannot be null");
        Objects.requireNonNull(executedQuantity, "executedQuantity cannot be null");
        Objects.requireNonNull(executedPrice, "executedPrice cannot be null");
        Objects.requireNonNull(fee, "fee cannot be null");
        Objects.requireNonNull(totalCost, "totalCost cannot be null");

        if (executedQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("executedQuantity must be positive");
        }
        if (executedPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("executedPrice must be positive");
        }
        if (totalCost.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("totalCost cannot be negative");
        }
    }
}
