package com.marmitt.core.dto.strategy;

import com.marmitt.core.domain.Symbol;
import com.marmitt.core.domain.portfolio.Asset;
import com.marmitt.core.domain.portfolio.Position;
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
 * Contém todos os dados necessários para que a Strategy possa tomar decisões informadas
 */
@Builder
public record PortfolioContextDto(
        UUID portfolioId,
        String portfolioName,
        Symbol symbol,                      // Single currency this portfolio manages
        Asset totalCapital,                 // Capital total do portfolio
        Asset availableBalance,             // Saldo disponível para novas operações
        Asset allocatedBalance,             // Saldo já alocado em posições
        Position position,                  // Single position (can be null)
        List<OpenBuyEntryDto> openTransactions, // Compras executadas (posições abertas)
        List<PendingSellEntryDto> pendingSellOrders, // Sells PENDING/SUBMITTED em trânsito
        BigDecimal realizedPnL,             // P&L acumulado das vendas realizadas
        BigDecimal minimumOperationAmount,  // Valor mínimo para operações
        BigDecimal maxExposurePerSymbol     // % máxima de exposição (agora sempre 100% para single currency)
) {

    public PortfolioContextDto {
        Objects.requireNonNull(portfolioId, "Portfolio ID cannot be null");
        Objects.requireNonNull(portfolioName, "Portfolio name cannot be null");
        Objects.requireNonNull(symbol, "Symbol cannot be null");
        Objects.requireNonNull(totalCapital, "Total capital cannot be null");
        Objects.requireNonNull(availableBalance, "Available balance cannot be null");
        Objects.requireNonNull(allocatedBalance, "Allocated balance cannot be null");
        // position can be null - no validation needed
        // openTransactions can be null or empty
        Objects.requireNonNull(realizedPnL, "Realized PnL cannot be null");
        Objects.requireNonNull(minimumOperationAmount, "Minimum operation amount cannot be null");
        Objects.requireNonNull(maxExposurePerSymbol, "Max exposure per currency cannot be null");
    }

    /**
     * Verifica se há saldo suficiente para uma operação
     */
    public boolean hasAvailableBalance(BigDecimal amount) {
        return availableBalance.amount().compareTo(amount) >= 0;
    }

    /**
     * Verifica se há saldo mínimo para operação
     */
    public boolean hasMinimumBalance() {
        return availableBalance.amount().compareTo(minimumOperationAmount) >= 0;
    }

    /**
     * Verifica se existe posição para o símbolo do portfolio
     */
    public boolean hasPosition() {
        return position != null && !position.isEmpty();
    }

    /**
     * Calcula valor atual da posição
     */
    public BigDecimal getCurrentPositionValue() {
        return position != null ? position.getCurrentValue().amount() : BigDecimal.ZERO;
    }

    /**
     * Calcula exposição restante permitida
     */
    public BigDecimal getRemainingExposure() {
        BigDecimal maxExposureValue = totalCapital.amount().multiply(maxExposurePerSymbol);
        BigDecimal currentExposure = getCurrentPositionValue();
        return maxExposureValue.subtract(currentExposure);
    }

    /**
     * Verifica se ainda há exposição disponível
     */
    public boolean hasRemainingExposure() {
        return getRemainingExposure().compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Calcula percentual de exposição atual
     */
    public BigDecimal getCurrentExposurePercentage() {
        if (totalCapital.amount().compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal currentValue = getCurrentPositionValue();
        return currentValue.divide(totalCapital.amount(), 4, RoundingMode.HALF_UP);
    }

    /**
     * Verifica se existem lotes de compra abertos
     */
    public boolean hasOpenLots() {
        return openTransactions != null && !openTransactions.isEmpty();
    }

    /**
     * Alias para hasOpenLots() — compatibilidade
     */
    public boolean hasOpenTransactions() {
        return hasOpenLots();
    }

    /**
     * Retorna a transação de compra mais antiga (útil para stop loss por tempo)
     */
    public Optional<OpenBuyEntryDto> getOldestOpenTransaction() {
        if (!hasOpenTransactions()) {
            return Optional.empty();
        }
        return openTransactions.stream()
                .min(Comparator.comparing(OpenBuyEntryDto::executedAt));
    }

    /**
     * Verifica se existem ordens de venda pendentes em trânsito
     */
    public boolean hasPendingSellOrders() {
        return pendingSellOrders != null && !pendingSellOrders.isEmpty();
    }

    /**
     * Retorna quantidade total de sells pendentes em trânsito
     */
    public BigDecimal getTotalPendingSellQuantity() {
        if (pendingSellOrders == null) return BigDecimal.ZERO;
        return pendingSellOrders.stream()
                .map(s -> s.quantity().amount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Retorna quantidade disponível para venda (posição - sells pendentes)
     */
    public BigDecimal getAvailableToSellQuantity() {
        if (!hasPosition()) return BigDecimal.ZERO;
        return position.getQuantity().amount().subtract(getTotalPendingSellQuantity()).max(BigDecimal.ZERO);
    }

    /**
     * Calcula o percentual de lucro de um lote específico comparando o executedPrice com o preço atual.
     * @param lotId ID do lote (buy transaction)
     * @param currentPrice preço de mercado atual
     * @return percentual de lucro (ex: 5.25 para +5.25%, -2.10 para -2.10%), ou empty se lote não encontrado
     */
    public Optional<BigDecimal> calculateLotProfit(UUID lotId, BigDecimal currentPrice) {
        if (openTransactions == null || currentPrice == null) return Optional.empty();
        return openTransactions.stream()
                .filter(lot -> lot.lotId().equals(lotId))
                .findFirst()
                .map(lot -> {
                    BigDecimal entryPrice = lot.executedPrice().amount();
                    if (entryPrice.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
                    return currentPrice.subtract(entryPrice)
                            .divide(entryPrice, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100));
                });
    }
}
