package com.marmitt.core.dto.strategy;

import com.marmitt.core.domain.Symbol;
import lombok.Builder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Contexto consolidado injetado na Strategy para tomada de decisao.
 * Mantem dados de capital, posicao agregada, lotes abertos e ordens em transito.
 */
@Builder
public record StrategyContextDto(
        UUID runnerId,
        UUID portfolioId,
        Symbol symbol,
        PositionContext positionContext,
        List<OpenLotDto> openLots,
        List<PendingOrderDto> pendingOrders,
        BigDecimal totalCapital,
        BigDecimal availableCapital,
        BigDecimal maxOperationAmount,
        BigDecimal minOperationAmount,
        int maxOpenPositions,
        int currentOpenPositions,
        BigDecimal realizedPnl,
        BigDecimal unrealizedPnl
) {
    public StrategyContextDto {
        Objects.requireNonNull(runnerId, "Runner ID cannot be null");
        Objects.requireNonNull(portfolioId, "Portfolio ID cannot be null");
        Objects.requireNonNull(symbol, "Symbol cannot be null");
        Objects.requireNonNull(positionContext, "Position context cannot be null");
        Objects.requireNonNull(openLots, "Open lots cannot be null");
        Objects.requireNonNull(pendingOrders, "Pending orders cannot be null");
        Objects.requireNonNull(totalCapital, "Total capital cannot be null");
        Objects.requireNonNull(availableCapital, "Available capital cannot be null");
        Objects.requireNonNull(maxOperationAmount, "Max operation amount cannot be null");
        Objects.requireNonNull(minOperationAmount, "Min operation amount cannot be null");
        Objects.requireNonNull(realizedPnl, "Realized pnl cannot be null");
        Objects.requireNonNull(unrealizedPnl, "Unrealized pnl cannot be null");
    }

    public boolean hasAvailableBalance(BigDecimal amount) {
        return availableCapital.compareTo(amount) >= 0;
    }

    public boolean hasMinimumBalance() {
        return availableCapital.compareTo(minOperationAmount) >= 0;
    }

    public boolean hasOpenLots() {
        return !openLots.isEmpty();
    }

    public Optional<OpenLotDto> getOldestOpenLot() {
        if (!hasOpenLots()) {
            return Optional.empty();
        }
        return openLots.stream()
                .min(Comparator.comparing(OpenLotDto::openedAt));
    }

    public boolean hasPendingOrders() {
        return !pendingOrders.isEmpty();
    }

    public BigDecimal getTotalPendingOrderQuantity() {
        return pendingOrders.stream()
                .map(PendingOrderDto::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Optional<BigDecimal> calculateLotProfit(UUID lotId, BigDecimal currentPrice) {
        if (currentPrice == null) return Optional.empty();
        return openLots.stream()
                .filter(lot -> lot.lotId().equals(lotId))
                .findFirst()
                .map(lot -> {
                    BigDecimal entryPrice = lot.entryPrice();
                    if (entryPrice.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
                    return currentPrice.subtract(entryPrice)
                            .divide(entryPrice, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100));
                });
    }
}
