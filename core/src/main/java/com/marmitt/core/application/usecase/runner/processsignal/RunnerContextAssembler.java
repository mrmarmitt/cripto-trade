package com.marmitt.core.application.usecase.runner.processsignal;

import com.marmitt.core.domain.Symbol;
import com.marmitt.core.domain.portfolio.GlobalBalance;
import com.marmitt.core.domain.runner.Position;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.dto.strategy.OpenBuyEntryDto;
import com.marmitt.core.dto.strategy.PendingSellEntryDto;
import com.marmitt.core.dto.strategy.PortfolioContextDto;
import com.marmitt.core.enums.TransactionStatus;
import com.marmitt.core.ports.outbound.repository.GlobalBalanceRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Monta o {@link PortfolioContextDto} consumido pela estrategia.
 * Reune saldo global, posicoes abertas e ordens de venda pendentes.
 */
class RunnerContextAssembler {

    private final GlobalBalanceRepositoryPort globalBalanceRepository;
    private final StrategyRunnerRepositoryPort strategyRunnerRepository;
    private final BigDecimal minimumOperationAmount;

    public RunnerContextAssembler(GlobalBalanceRepositoryPort globalBalanceRepository,
                                  StrategyRunnerRepositoryPort strategyRunnerRepository,
                                  BigDecimal minimumOperationAmount) {
        this.globalBalanceRepository = globalBalanceRepository;
        this.strategyRunnerRepository = strategyRunnerRepository;
        this.minimumOperationAmount = minimumOperationAmount;
    }

    /**
     * Constroi o contexto de portfolio para execucao da estrategia do runner.
     */
    public PortfolioContextDto assemble(StrategyRunner runner) {
        GlobalBalance balance = globalBalanceRepository
                .findByPortfolioId(runner.getPortfolioId())
                .orElseThrow(() -> new IllegalStateException(
                        "GlobalBalance not found for portfolio: " + runner.getPortfolioId()));

        Symbol symbol = Symbol.of(runner.getSymbol());
        Optional<Position> runnerPositionOpt =
                strategyRunnerRepository.findOpenPositionByRunnerIdAndSymbol(runner.getId(), runner.getSymbol());

        List<OpenBuyEntryDto> openBuyEntries = runnerPositionOpt
                .map(this::buildOpenBuyEntry)
                .map(List::of)
                .orElse(List.of());

        List<PendingSellEntryDto> pendingSellOrders = buildPendingSellOrders(runner);

        return PortfolioContextDto.builder()
                .portfolioId(runner.getPortfolioId())
                .portfolioName(runner.getStrategyName())
                .symbol(symbol)
                .totalCapital(balance.getTotalBalance())
                .availableBalance(balance.getAvailableBalance())
                .allocatedBalance(balance.getReservedBalance())
                .openTransactions(openBuyEntries)
                .pendingSellOrders(pendingSellOrders)
                .realizedPnL(balance.getRealizedBalance())
                .minimumOperationAmount(minimumOperationAmount)
                .maxExposurePerSymbol(runner.getMaxAllocationPercent())
                .build();
    }

    private OpenBuyEntryDto buildOpenBuyEntry(Position pos) {
        BigDecimal lockedQty = pos.getLockedQuantity() != null
                ? pos.getLockedQuantity() : BigDecimal.ZERO;
        BigDecimal remaining = pos.getQuantity().subtract(lockedQty).max(BigDecimal.ZERO);

        return OpenBuyEntryDto.builder()
                .lotId(pos.getId())
                .executedQuantity(pos.getQuantity())
                .executedPrice(pos.getAveragePrice())
                .remainingQuantity(remaining)
                .reservedQuantity(lockedQty)
                .executedAt(pos.getOpenedAt())
                .build();
    }

    private List<PendingSellEntryDto> buildPendingSellOrders(StrategyRunner runner) {
        return strategyRunnerRepository
                .findByRunnerIdAndStatuses(runner.getId(),
                        List.of(TransactionStatus.PENDING, TransactionStatus.SUBMITTED))
                .stream()
                .filter(com.marmitt.core.domain.runner.Transaction::isSell)
                .map(t -> PendingSellEntryDto.builder()
                        .transactionId(t.getId())
                        .quantity(t.getQuantity())
                        .price(t.getPrice())
                        .status(t.getStatus())
                        .requestedAt(t.getRequestedAt())
                        .build())
                .toList();
    }
}
