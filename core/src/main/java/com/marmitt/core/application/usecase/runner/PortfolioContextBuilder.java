package com.marmitt.core.application.usecase.runner;

import com.marmitt.core.domain.Symbol;
import com.marmitt.core.domain.portfolio.Asset;
import com.marmitt.core.domain.portfolio.GlobalBalance;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.dto.strategy.OpenBuyEntryDto;
import com.marmitt.core.dto.strategy.PendingSellEntryDto;
import com.marmitt.core.dto.strategy.PortfolioContextDto;
import com.marmitt.core.enums.TransactionStatus;
import com.marmitt.core.ports.outbound.repository.GlobalBalanceRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Constrói o {@link PortfolioContextDto} a partir do estado corrente do Runner no novo domínio.
 * <p>
 * Atua como bridge entre o domínio novo ({@code domain.runner}) e o contrato de entrada
 * das strategies ({@code PortfolioContextDto}), que ainda referencia tipos legados
 * ({@code domain.portfolio.Position}, {@code Asset}).
 * <p>
 * <b>Bridge temporário:</b> {@code domain.portfolio.Position} é {@code @Deprecated(forRemoval = true)}.
 * Quando as strategies migrarem para uma interface baseada em {@code BigDecimal},
 * esta classe e a dependência do tipo legado serão removidas.
 * <p>
 * <b>Simplificações V1:</b>
 * <ul>
 *   <li>{@code openTransactions}: uma entrada por {@code Position} aberta (sem rastreamento por lote)</li>
 *   <li>{@code remainingQuantity}: quantity total menos lockedQuantity</li>
 *   <li>{@code minimumOperationAmount}: valor fixo de 10 (configurável em V2)</li>
 *   <li>{@code portfolioName}: usa {@code strategyName} do Runner como proxy</li>
 * </ul>
 *
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 6.2.1</a>
 */
@Slf4j
public class PortfolioContextBuilder {

    private static final BigDecimal MINIMUM_OPERATION_AMOUNT = BigDecimal.valueOf(10);

    private final StrategyRunnerRepositoryPort runnerRepository;
    private final GlobalBalanceRepositoryPort globalBalanceRepository;

    public PortfolioContextBuilder(
            StrategyRunnerRepositoryPort runnerRepository,
            GlobalBalanceRepositoryPort globalBalanceRepository
    ) {
        this.runnerRepository = runnerRepository;
        this.globalBalanceRepository = globalBalanceRepository;
    }

    /**
     * Constrói o contexto do portfolio para a estratégia do Runner.
     *
     * @param runner runner cujo contexto será construído
     * @return {@link PortfolioContextDto} com o estado atual do portfolio
     * @throws IllegalStateException se o GlobalBalance não for encontrado
     */
    public PortfolioContextDto build(StrategyRunner runner) {
        GlobalBalance balance = globalBalanceRepository
                .findByPortfolioId(runner.getPortfolioId())
                .orElseThrow(() -> new IllegalStateException(
                        "GlobalBalance not found for portfolio: " + runner.getPortfolioId()));

        String currency = balance.getBaseCurrency();
        Symbol symbol = Symbol.of(runner.getSymbol());
        String baseAsset = symbol.getBaseAsset();

        // Posição aberta do Runner (pode ser null)
        Optional<com.marmitt.core.domain.runner.Position> runnerPositionOpt =
                runnerRepository.findOpenPositionByRunnerIdAndSymbol(runner.getId(), runner.getSymbol());

        // Bridge para Position legada (deprecated — bridge temporário)
        com.marmitt.core.domain.portfolio.Position legacyPosition =
                runnerPositionOpt.map(pos -> buildLegacyPosition(pos, symbol, baseAsset, currency))
                        .orElse(null);

        // Entradas de compra abertas (uma por posição — simplificação V1)
        List<OpenBuyEntryDto> openBuyEntries = runnerPositionOpt
                .map(pos -> buildOpenBuyEntry(pos, baseAsset, currency))
                .map(List::of)
                .orElse(List.of());

        // Ordens de venda em trânsito (PENDING + SUBMITTED do tipo SELL)
        List<PendingSellEntryDto> pendingSellOrders = buildPendingSellOrders(runner, baseAsset, currency);

        return PortfolioContextDto.builder()
                .portfolioId(runner.getPortfolioId())
                .portfolioName(runner.getStrategyName())
                .symbol(symbol)
                .totalCapital(Asset.stableCoin(balance.getTotalBalance(), currency))
                .availableBalance(Asset.stableCoin(balance.getAvailableBalance(), currency))
                .allocatedBalance(Asset.stableCoin(balance.getReservedBalance(), currency))
                .position(legacyPosition)
                .openTransactions(openBuyEntries)
                .pendingSellOrders(pendingSellOrders)
                .realizedPnL(balance.getRealizedBalance())
                .minimumOperationAmount(MINIMUM_OPERATION_AMOUNT)
                .maxExposurePerSymbol(runner.getMaxAllocationPercent())
                .build();
    }

    // ============================================================
    // Private helpers
    // ============================================================

    @SuppressWarnings("deprecation")
    private com.marmitt.core.domain.portfolio.Position buildLegacyPosition(
            com.marmitt.core.domain.runner.Position pos,
            Symbol symbol, String baseAsset, String currency) {

        Asset quantity = Asset.crypto(pos.getQuantity(), baseAsset);
        Asset avgPrice = Asset.stableCoin(pos.getAveragePrice(), currency);

        com.marmitt.core.domain.portfolio.Position legacy =
                new com.marmitt.core.domain.portfolio.Position(symbol, quantity, avgPrice);

        if (pos.getCurrentPrice() != null
                && pos.getCurrentPrice().compareTo(BigDecimal.ZERO) > 0) {
            legacy.updateCurrentPrice(Asset.stableCoin(pos.getCurrentPrice(), currency));
        }

        return legacy;
    }

    private OpenBuyEntryDto buildOpenBuyEntry(
            com.marmitt.core.domain.runner.Position pos,
            String baseAsset, String currency) {

        BigDecimal lockedQty = pos.getLockedQuantity() != null
                ? pos.getLockedQuantity() : BigDecimal.ZERO;
        BigDecimal remaining = pos.getQuantity().subtract(lockedQty).max(BigDecimal.ZERO);

        return OpenBuyEntryDto.builder()
                .lotId(pos.getId())
                .executedQuantity(Asset.crypto(pos.getQuantity(), baseAsset))
                .executedPrice(Asset.stableCoin(pos.getAveragePrice(), currency))
                .remainingQuantity(Asset.crypto(remaining, baseAsset))
                .reservedQuantity(Asset.crypto(lockedQty, baseAsset))
                .executedAt(pos.getOpenedAt())
                .build();
    }

    private List<PendingSellEntryDto> buildPendingSellOrders(
            StrategyRunner runner, String baseAsset, String currency) {

        return runnerRepository
                .findByRunnerIdAndStatuses(runner.getId(),
                        List.of(TransactionStatus.PENDING, TransactionStatus.SUBMITTED))
                .stream()
                .filter(com.marmitt.core.domain.runner.Transaction::isSell)
                .map(t -> PendingSellEntryDto.builder()
                        .transactionId(t.getId())
                        .quantity(Asset.crypto(t.getQuantity(), baseAsset))
                        .price(Asset.stableCoin(t.getPrice(), currency))
                        .status(t.getStatus())
                        .requestedAt(t.getRequestedAt())
                        .build())
                .toList();
    }
}
