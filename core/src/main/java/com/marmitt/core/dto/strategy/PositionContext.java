package com.marmitt.core.dto.strategy;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.time.Instant;

/**
 * Objeto imutavel injetado na Strategy pelo Runner, contendo o estado operacional
 * necessario para decisao de trading. Read-only - a Strategy nao modifica este objeto.
 *
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Secao 3.4.3, Blueprint 9.3.D</a>
 */
@Builder
public record PositionContext(
        UUID positionId,
        String symbol,
        BigDecimal quantity,
        BigDecimal averagePrice,
        BigDecimal currentPrice,
        BigDecimal unrealizedPnl,
        BigDecimal realizedPnl,
        List<OpenLotDto> openLots
) {
    /**
     * Verifica se ha posicao aberta.
     */
    public boolean hasOpenPosition() {
        return positionId != null && quantity != null && quantity.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Cria um PositionContext vazio (sem posicao aberta).
     */
    public static PositionContext empty(String symbol) {
        return PositionContext.builder()
                .symbol(symbol)
                .quantity(BigDecimal.ZERO)
                .averagePrice(BigDecimal.ZERO)
                .currentPrice(BigDecimal.ZERO)
                .unrealizedPnl(BigDecimal.ZERO)
                .realizedPnl(BigDecimal.ZERO)
                .openLots(List.of())
                .build();
    }

    public static PositionContext from(UUID positionId,
                                       String symbol,
                                       BigDecimal quantity,
                                       BigDecimal averagePrice,
                                       BigDecimal currentPrice,
                                       BigDecimal realizedPnl,
                                       Instant openedAt,
                                       List<OpenLotDto> openLots) {
        Objects.requireNonNull(positionId, "positionId cannot be null");
        Objects.requireNonNull(symbol, "symbol cannot be null");
        Objects.requireNonNull(quantity, "quantity cannot be null");
        Objects.requireNonNull(averagePrice, "averagePrice cannot be null");
        Objects.requireNonNull(realizedPnl, "realizedPnl cannot be null");
        Objects.requireNonNull(openLots, "openLots cannot be null");

        BigDecimal marketPrice = currentPrice != null ? currentPrice : averagePrice;
        BigDecimal unrealized = marketPrice.subtract(averagePrice).multiply(quantity);

        return PositionContext.builder()
                .positionId(positionId)
                .symbol(symbol)
                .quantity(quantity)
                .averagePrice(averagePrice)
                .currentPrice(marketPrice)
                .unrealizedPnl(unrealized)
                .realizedPnl(realizedPnl)
                .openLots(openLots)
                .build();
    }
}
