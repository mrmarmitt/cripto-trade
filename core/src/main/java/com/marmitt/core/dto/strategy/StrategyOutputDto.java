package com.marmitt.core.dto.strategy;

import com.marmitt.core.enums.TradingAction;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Builder
public record StrategyOutputDto(
        String strategyName,
        TradingAction decision,
        BigDecimal confidence,    // 0.0 - 1.0
        BigDecimal quantity,      // Quantidade absoluta a ser executada (ex: 0.05 BTC)
        BigDecimal limitPrice,    // preço-limite da ordem; null = usar o preço de mercado do tick (comportamento default)
        UUID targetLotId,         // null = FIFO, UUID = lot especifico
        UUID targetTransactionId, // ordem em transito a cancelar (somente SHOULD_CANCEL)
        String reasoning,
        Instant timestamp,
        Map<String, Object> metadata
) {

    public static StrategyOutputDto hold(String strategyName, String reasoning) {
        return new StrategyOutputDto(strategyName, TradingAction.SHOULD_HOLD,
                                 BigDecimal.ZERO, BigDecimal.ZERO, null, null, null, reasoning,
                                 Instant.now(), Map.of());
    }

    public static StrategyOutputDto buy(String strategyName, BigDecimal confidence,
                                        BigDecimal quantity, String reasoning) {
        validateTradeParams(confidence, quantity);
        return new StrategyOutputDto(strategyName, TradingAction.SHOULD_BUY,
                                 confidence, quantity, null, null, null, reasoning,
                                 Instant.now(), Map.of());
    }

    /**
     * BUY com preço-limite explícito. O {@code limitPrice} (após normalização às regras de filtro
     * da exchange) é o único valor que persiste, reserva capital e é enviado à exchange.
     */
    public static StrategyOutputDto buyAt(String strategyName, BigDecimal confidence,
                                          BigDecimal quantity, BigDecimal limitPrice, String reasoning) {
        validateTradeParams(confidence, quantity);
        requirePositiveLimitPrice(limitPrice);
        return new StrategyOutputDto(strategyName, TradingAction.SHOULD_BUY,
                                 confidence, quantity, limitPrice, null, null, reasoning,
                                 Instant.now(), Map.of());
    }

    public static StrategyOutputDto sell(String strategyName, BigDecimal confidence,
                                         BigDecimal quantity, String reasoning) {
        validateTradeParams(confidence, quantity);
        return new StrategyOutputDto(strategyName, TradingAction.SHOULD_SELL,
                                 confidence, quantity, null, null, null, reasoning,
                                 Instant.now(), Map.of());
    }

    /**
     * SELL com preço-limite explícito. Mesma semântica de preço de {@link #buyAt}.
     */
    public static StrategyOutputDto sellAt(String strategyName, BigDecimal confidence,
                                           BigDecimal quantity, BigDecimal limitPrice, String reasoning) {
        validateTradeParams(confidence, quantity);
        requirePositiveLimitPrice(limitPrice);
        return new StrategyOutputDto(strategyName, TradingAction.SHOULD_SELL,
                                 confidence, quantity, limitPrice, null, null, reasoning,
                                 Instant.now(), Map.of());
    }

    public static StrategyOutputDto sellLot(String strategyName, BigDecimal confidence,
                                            BigDecimal quantity, UUID targetLotId, String reasoning) {
        validateTradeParams(confidence, quantity);
        return new StrategyOutputDto(strategyName, TradingAction.SHOULD_SELL,
                                 confidence, quantity, null, targetLotId, null, reasoning,
                                 Instant.now(), Map.of());
    }

    /**
     * Decisao de cancelar uma ordem em transito. Nao reserva capital nem normaliza quantidade —
     * apenas identifica o alvo pelo {@code targetTransactionId} (de {@link PendingOrderDto#transactionId}).
     */
    public static StrategyOutputDto cancel(String strategyName, UUID targetTransactionId, String reasoning) {
        Objects.requireNonNull(targetTransactionId, "targetTransactionId required for cancel");
        return new StrategyOutputDto(strategyName, TradingAction.SHOULD_CANCEL,
                                 BigDecimal.ZERO, BigDecimal.ZERO, null, null, targetTransactionId, reasoning,
                                 Instant.now(), Map.of());
    }

    private static void validateTradeParams(BigDecimal confidence, BigDecimal quantity) {
        if (confidence == null || confidence.compareTo(BigDecimal.ZERO) < 0 || confidence.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Confidence must be between 0.0 and 1.0");
        }
        if (quantity != null && quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantity must be positive when specified");
        }
    }

    private static void requirePositiveLimitPrice(BigDecimal limitPrice) {
        if (limitPrice == null || limitPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("limitPrice must be positive");
        }
    }

    public boolean shouldTrade() {
        return decision == TradingAction.SHOULD_BUY || decision == TradingAction.SHOULD_SELL;
    }

    public boolean shouldHold() {
        return decision == TradingAction.SHOULD_HOLD;
    }

    public boolean shouldCancel() {
        return decision == TradingAction.SHOULD_CANCEL;
    }

    public boolean isHighConfidence() {
        return confidence != null && confidence.compareTo(new BigDecimal("0.7")) >= 0;
    }

}
