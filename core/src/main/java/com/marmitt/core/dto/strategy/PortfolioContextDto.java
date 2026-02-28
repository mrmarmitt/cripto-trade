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
 * Contexto do Portfolio fornecido para a Strategy
 * Contem todos os dados necessarios para que a Strategy possa tomar decisoes informadas
 */
@Builder
public record PortfolioContextDto(
        UUID portfolioId,
        Symbol symbol,                      // Single currency this portfolio manages
        BigDecimal totalCapital,                 // Capital total do portfolio
        BigDecimal availableBalance,             // Saldo disponivel para novas operacoes
        BigDecimal allocatedBalance,             // Saldo ja alocado em posicoes
        List<OpenLotDto> openLots, // Lotes de compra abertos
        List<PendingOrderDto> pendingOrders, // Ordens BUY/SELL em transito
        BigDecimal realizedPnL,             // P&L acumulado das vendas realizadas
        BigDecimal minimumOperationAmount,  // Valor minimo para operacoes
        BigDecimal maxExposurePerSymbol     // % maxima de exposicao (agora sempre 100% para single currency)
) {

    public PortfolioContextDto {
        Objects.requireNonNull(portfolioId, "Portfolio ID cannot be null");
        Objects.requireNonNull(symbol, "Symbol cannot be null");
        Objects.requireNonNull(totalCapital, "Total capital cannot be null");
        Objects.requireNonNull(availableBalance, "Available balance cannot be null");
        Objects.requireNonNull(allocatedBalance, "Allocated balance cannot be null");
        // position can be null - no validation needed
        // openLots can be null or empty
        Objects.requireNonNull(realizedPnL, "Realized PnL cannot be null");
        Objects.requireNonNull(minimumOperationAmount, "Minimum operation amount cannot be null");
        Objects.requireNonNull(maxExposurePerSymbol, "Max exposure per currency cannot be null");
    }

    /**
     * Verifica se ha saldo suficiente para uma operacao
     */
    public boolean hasAvailableBalance(BigDecimal amount) {
        return availableBalance.compareTo(amount) >= 0;
    }

    /**
     * Verifica se ha saldo minimo para operacao
     */
    public boolean hasMinimumBalance() {
        return availableBalance.compareTo(minimumOperationAmount) >= 0;
    }


    /**
     * Verifica se existem lotes de compra abertos
     */
    public boolean hasOpenLots() {
        return openLots != null && !openLots.isEmpty();
    }

    /**
     * Retorna o lote de compra mais antigo (util para stop loss por tempo)
     */
    public Optional<OpenLotDto> getOldestOpenLot() {
        if (!hasOpenLots()) {
            return Optional.empty();
        }
        return openLots.stream()
                .min(Comparator.comparing(OpenLotDto::openedAt));
    }

    /**
     * Verifica se existem ordens pendentes em transito
     */
    public boolean hasPendingOrders() {
        return pendingOrders != null && !pendingOrders.isEmpty();
    }

    /**
     * Retorna quantidade total de ordens pendentes em transito
     */
    public BigDecimal getTotalPendingOrderQuantity() {
        if (pendingOrders == null) return BigDecimal.ZERO;
        return pendingOrders.stream()
                .map(PendingOrderDto::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Calcula o percentual de lucro de um lote especifico comparando o entryPrice com o preco atual.
     * @param lotId ID do lote (Position)
     * @param currentPrice preco de mercado atual
     * @return percentual de lucro (ex: 5.25 para +5.25%, -2.10 para -2.10%), ou empty se lote nao encontrado
     */
    public Optional<BigDecimal> calculateLotProfit(UUID lotId, BigDecimal currentPrice) {
        if (openLots == null || currentPrice == null) return Optional.empty();
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
