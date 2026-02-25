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
 * Contém todos os dados necessários para que a Strategy possa tomar decisões informadas
 */
@Builder
public record PortfolioContextDto(
        UUID portfolioId,
        Symbol symbol,                      // Single currency this portfolio manages
        BigDecimal totalCapital,                 // Capital total do portfolio
        BigDecimal availableBalance,             // Saldo disponível para novas operações
        BigDecimal allocatedBalance,             // Saldo já alocado em posições
        List<OpenBuyEntryDto> openTransactions, // Compras executadas (posições abertas)
        List<PendingSellEntryDto> pendingSellOrders, // Sells PENDING/SUBMITTED em trânsito
        BigDecimal realizedPnL,             // P&L acumulado das vendas realizadas
        BigDecimal minimumOperationAmount,  // Valor mínimo para operações
        BigDecimal maxExposurePerSymbol     // % máxima de exposição (agora sempre 100% para single currency)
) {

    public PortfolioContextDto {
        Objects.requireNonNull(portfolioId, "Portfolio ID cannot be null");
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
        return availableBalance.compareTo(amount) >= 0;
    }

    /**
     * Verifica se há saldo mínimo para operação
     */
    public boolean hasMinimumBalance() {
        return availableBalance.compareTo(minimumOperationAmount) >= 0;
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
                .map(PendingSellEntryDto::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
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
                    BigDecimal entryPrice = lot.executedPrice();
                    if (entryPrice.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
                    return currentPrice.subtract(entryPrice)
                            .divide(entryPrice, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100));
                });
    }
}
