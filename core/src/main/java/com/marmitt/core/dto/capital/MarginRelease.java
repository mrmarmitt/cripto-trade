package com.marmitt.core.dto.capital;

import com.marmitt.core.enums.ReleaseReason;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Payload da devolução de margem enviada pelo Runner ao Portfolio quando uma
 * Transaction atinge estado terminal sem execução total.
 * Entrada do método assíncrono {@code CapitalManager.release()}.
 * <p>
 * {@code releaseAmount = originalReserved - executedAmount}.<br>
 * Para REJECTED/EXPIRED: {@code executedAmount = ZERO} → estorno total da reserva.<br>
 * Para CANCELED parcial: {@code executedAmount > ZERO} → estorno proporcional.
 * <p>
 * O Portfolio usa {@code transactionId} para garantir idempotência — duplicatas descartadas.
 * Este evento <b>nunca pode ser perdido</b> — o Runner persiste localmente e reenvia em retry.
 *
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 5.2.3</a>
 */
public record MarginRelease(
        UUID transactionId,
        UUID runnerId,
        BigDecimal releaseAmount,
        ReleaseReason reason,
        BigDecimal executedAmount
) {
    public MarginRelease {
        Objects.requireNonNull(transactionId, "transactionId cannot be null");
        Objects.requireNonNull(runnerId, "runnerId cannot be null");
        Objects.requireNonNull(releaseAmount, "releaseAmount cannot be null");
        Objects.requireNonNull(reason, "reason cannot be null");
        Objects.requireNonNull(executedAmount, "executedAmount cannot be null");

        if (releaseAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("releaseAmount must be positive");
        }
        if (executedAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("executedAmount cannot be negative");
        }
    }

    /**
     * Cria um release de estorno total (REJECTED ou EXPIRED).
     */
    public static MarginRelease fullRelease(UUID transactionId, UUID runnerId,
                                            BigDecimal reservedAmount, ReleaseReason reason) {
        return new MarginRelease(transactionId, runnerId, reservedAmount, reason, BigDecimal.ZERO);
    }

    /**
     * Cria um release de estorno parcial (CANCELED após execução parcial).
     */
    public static MarginRelease partialRelease(UUID transactionId, UUID runnerId,
                                               BigDecimal releaseAmount, BigDecimal executedAmount) {
        return new MarginRelease(transactionId, runnerId, releaseAmount, ReleaseReason.CANCELED, executedAmount);
    }
}
