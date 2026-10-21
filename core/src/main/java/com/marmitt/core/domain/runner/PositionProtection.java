package com.marmitt.core.domain.runner;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Limites de proteção do lote do runner, avaliados pelo core a cada tick sem consultar a estratégia
 * (T44). Cobrem a estratégia indisponível, desabilitada ou que só devolve HOLD: com eles, nenhuma
 * queda deixa o lote aberto indefinidamente.
 *
 * <ul>
 *   <li>{@code maxLossPercentPerLot} — perda máxima do lote em relação ao custo pago por ele, como
 *       fração em (0, 1) (mesma convenção de {@code maxAllocationPercent}). Default
 *       {@link #DEFAULT_MAX_LOSS_PERCENT_PER_LOT} para todo runner.</li>
 *   <li>{@code maxHoldingDuration} — idade máxima do lote; {@code null} desliga (default).</li>
 * </ul>
 */
public record PositionProtection(BigDecimal maxLossPercentPerLot, Duration maxHoldingDuration) {

    /**
     * Inicio do {@code reasoning} da venda protetiva. A linha do tempo de eventos (T59) reconhece a saida
     * protetiva por ele.
     */
    public static final String EXIT_REASONING_PREFIX = "protective exit ";

    /** Decisão de negócio da T44: 5% por lote, abaixo do disjuntor de drawdown de 10% do portfólio. */
    public static final BigDecimal DEFAULT_MAX_LOSS_PERCENT_PER_LOT = new BigDecimal("0.05");

    public PositionProtection {
        Objects.requireNonNull(maxLossPercentPerLot, "maxLossPercentPerLot cannot be null");
        if (maxLossPercentPerLot.signum() <= 0 || maxLossPercentPerLot.compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalArgumentException("maxLossPercentPerLot must be in (0, 1): " + maxLossPercentPerLot);
        }
        if (maxHoldingDuration != null && maxHoldingDuration.compareTo(Duration.ofSeconds(1)) < 0) {
            throw new IllegalArgumentException("maxHoldingDuration must be at least 1s or null: " + maxHoldingDuration);
        }
    }

    public static PositionProtection defaults() {
        return new PositionProtection(DEFAULT_MAX_LOSS_PERCENT_PER_LOT, null);
    }

    /**
     * Limite que o lote viola ao preço informado, se algum. A perda vence a idade quando as duas valem.
     *
     * @param lot   lote aberto do runner
     * @param price preço de mercado do tick (quote asset)
     * @param now   instante da avaliação
     */
    public Optional<Violation> violation(Position lot, BigDecimal price, Instant now) {
        BigDecimal cost = lot.getCostBasis();
        BigDecimal loss = cost.subtract(price.multiply(lot.getQuantity()));
        if (cost.signum() > 0 && loss.compareTo(cost.multiply(maxLossPercentPerLot)) >= 0) {
            return Optional.of(new Violation(Reason.MAX_LOSS,
                    "loss " + loss.toPlainString() + " >= " + maxLossPercentPerLot.toPlainString() + " of cost "
                            + cost.toPlainString()));
        }
        if (maxHoldingDuration != null && !lot.getOpenedAt().plus(maxHoldingDuration).isAfter(now)) {
            return Optional.of(new Violation(Reason.MAX_HOLDING,
                    "lot open since " + lot.getOpenedAt() + " exceeds " + maxHoldingDuration));
        }
        return Optional.empty();
    }

    public enum Reason {
        MAX_LOSS,
        MAX_HOLDING
    }

    public record Violation(Reason reason, String detail) {
    }
}
