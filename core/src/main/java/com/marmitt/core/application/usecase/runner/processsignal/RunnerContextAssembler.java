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
 * Monta o {@link PortfolioContextDto} que a estrategia usa para tomar sua decisao de trade.
 *
 * <p>O contexto e uma "fotografia" do estado financeiro do runner no momento do tick:
 * <ul>
 *   <li><b>Saldo disponivel e total:</b> permite a estrategia calcular quanto capital
 *       pode ser alocado em um novo BUY sem violar os limites do portfolio.</li>
 *   <li><b>Posicoes abertas ({@code openTransactions}):</b> lotes BUY executados ainda
 *       nao vendidos. A quantidade restante (total menos quantidade bloqueada por SELL
 *       pendente) e informada separadamente para que a estrategia saiba o que pode vender.</li>
 *   <li><b>Ordens de venda pendentes ({@code pendingSellOrders}):</b> transacoes SELL
 *       nos status PENDING ou SUBMITTED que ainda nao foram confirmadas pela exchange.
 *       Permite a estrategia evitar enviar uma segunda SELL para a mesma posicao.</li>
 *   <li><b>Limite de exposicao ({@code maxExposurePerSymbol}):</b> percentual maximo
 *       do capital total que pode estar alocado neste runner simultaneamente.</li>
 * </ul>
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
     * Constroi o contexto de portfolio no momento do tick para o runner informado.
     *
     * @throws IllegalStateException se o {@link com.marmitt.core.domain.portfolio.GlobalBalance}
     *         do portfolio nao existir — indica inconsistencia de dados, nao cenario normal
     */
    public PortfolioContextDto assemble(StrategyRunner runner) {
        GlobalBalance balance = globalBalanceRepository
                .findByPortfolioId(runner.getPortfolioId())
                .orElseThrow(() -> new IllegalStateException(
                        "GlobalBalance not found for portfolio: " + runner.getPortfolioId()));

        Symbol symbol = Symbol.of(runner.getSymbol());
        Optional<Position> runnerPositionOpt =
                strategyRunnerRepository.findActivePositionByRunnerIdAndSymbol(runner.getId(), runner.getSymbol());

        List<OpenBuyEntryDto> openBuyEntries = runnerPositionOpt
                .map(this::buildOpenBuyEntry)
                .map(List::of)
                .orElse(List.of());

        List<PendingSellEntryDto> pendingSellOrders = buildPendingSellOrders(runner);

        return PortfolioContextDto.builder()
                .portfolioId(runner.getPortfolioId())
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
                        List.of(TransactionStatus.PENDING, TransactionStatus.SUBMITTED, TransactionStatus.PARTIAL))
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
