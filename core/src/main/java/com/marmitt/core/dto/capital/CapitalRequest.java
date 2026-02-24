package com.marmitt.core.dto.capital;

import com.marmitt.core.enums.TransactionType;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Payload da solicitação de reserva de capital enviada pelo Runner ao Portfolio.
 * Entrada do método síncrono {@code CapitalManager.reserve()}.
 * <p>
 * {@code amount} inclui o safety buffer aplicado pelo Runner antes da chamada
 * (ex: {@code rawAmount * 1.005} para cobrir slippage e fee estimada).
 *
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 5.2.1, 7.2</a>
 */
public record CapitalRequest(
        UUID transactionId,
        UUID runnerId,
        String runnerShortCode,
        String symbol,
        BigDecimal amount,
        TransactionType type
) {
    public CapitalRequest {
        Objects.requireNonNull(transactionId, "transactionId cannot be null");
        Objects.requireNonNull(runnerId, "runnerId cannot be null");
        Objects.requireNonNull(runnerShortCode, "runnerShortCode cannot be null");
        Objects.requireNonNull(symbol, "symbol cannot be null");
        Objects.requireNonNull(amount, "amount cannot be null");
        Objects.requireNonNull(type, "type cannot be null");

        if (runnerShortCode.isBlank()) {
            throw new IllegalArgumentException("runnerShortCode cannot be blank");
        }
        if (symbol.isBlank()) {
            throw new IllegalArgumentException("symbol cannot be blank");
        }
        if (amount.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("amount cannot be zero");
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("amount cannot be negative");
        }
    }
}
