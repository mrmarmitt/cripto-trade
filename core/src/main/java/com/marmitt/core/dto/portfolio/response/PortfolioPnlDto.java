package com.marmitt.core.dto.portfolio.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * PnL do portfolio. O flutuante marca cada lote aberto ao ultimo preco conhecido; lote ainda sem preco
 * (ex.: logo apos restart) fica de fora e e contado em {@code unpricedPositions} (T58).
 *
 * @param unrealizedPnL     flutuante dos lotes com preco; parcial quando {@code unpricedPositions > 0}
 * @param unpricedPositions lotes abertos fora do flutuante por falta de preco
 * @param priceAsOf         instante do preco mais antigo usado no flutuante; nulo sem lote com preco
 */
@Builder
public record PortfolioPnlDto(
        UUID portfolioId,
        BigDecimal realizedPnL,
        BigDecimal unrealizedPnL,
        BigDecimal totalPnL,
        int unpricedPositions,
        Instant priceAsOf
) {
}

