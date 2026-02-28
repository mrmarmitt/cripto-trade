package com.marmitt.core.dto.strategy;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

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
}
