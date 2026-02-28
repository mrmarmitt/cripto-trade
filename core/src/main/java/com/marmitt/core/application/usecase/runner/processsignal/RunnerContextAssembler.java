package com.marmitt.core.application.usecase.runner.processsignal;

import com.marmitt.core.domain.Symbol;
import com.marmitt.core.domain.portfolio.GlobalBalance;
import com.marmitt.core.domain.runner.Position;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.dto.strategy.OpenLotDto;
import com.marmitt.core.dto.strategy.PendingOrderDto;
import com.marmitt.core.dto.strategy.PositionContext;
import com.marmitt.core.dto.strategy.StrategyContextDto;
import com.marmitt.core.enums.TradingAction;
import com.marmitt.core.enums.TransactionStatus;
import com.marmitt.core.ports.outbound.repository.GlobalBalanceRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * Monta o {@link StrategyContextDto} que a estrategia usa para tomar sua decisao de trade.
 *
 * <p>O contexto e uma "fotografia" do estado financeiro do runner no momento do tick:
 * <ul>
 *   <li><b>Saldo disponivel e total:</b> permite a estrategia calcular quanto capital
 *       pode ser alocado em um novo BUY sem violar os limites do portfolio.</li>
 *   <li><b>Posicoes abertas ({@code openLots}):</b> lotes BUY executados ainda
 *       nao vendidos. A quantidade restante (total menos quantidade bloqueada por SELL
 *       pendente) e informada separadamente para que a estrategia saiba o que pode vender.</li>
 *   <li><b>Ordens pendentes ({@code pendingOrders}):</b> transacoes BUY/SELL
 *       nos status PENDING, SUBMITTED ou PARTIAL que ainda nao foram confirmadas pela exchange.</li>
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
     *         do portfolio nao existir - indica inconsistencia de dados, nao cenario normal
     */
    public StrategyContextDto assemble(StrategyRunner runner) {
        GlobalBalance balance = globalBalanceRepository
                .findByPortfolioId(runner.getPortfolioId())
                .orElseThrow(() -> new IllegalStateException(
                        "GlobalBalance not found for portfolio: " + runner.getPortfolioId()));

        Symbol symbol = Symbol.of(runner.getSymbol());
        Optional<Position> runnerPositionOpt =
                strategyRunnerRepository.findActivePositionByRunnerIdAndSymbol(runner.getId(), runner.getSymbol());

        List<OpenLotDto> openLots = runnerPositionOpt
                .map(this::buildOpenLot)
                .map(List::of)
                .orElse(List.of());

        List<PendingOrderDto> pendingOrders = buildPendingOrders(runner);

        PositionContext positionContext = runnerPositionOpt
                .map(position -> PositionContext.from(
                        position.getId(),
                        position.getSymbol(),
                        position.getQuantity(),
                        position.getAveragePrice(),
                        position.getCurrentPrice(),
                        position.getRealizedPnl(),
                        position.getOpenedAt(),
                        openLots
                ))
                .orElse(PositionContext.empty(runner.getSymbol()));

        BigDecimal maxOperationAmount = balance.getAvailableBalance();

        return StrategyContextDto.builder()
                .runnerId(runner.getId())
                .portfolioId(runner.getPortfolioId())
                .symbol(symbol)
                .positionContext(positionContext)
                .openLots(openLots)
                .pendingOrders(pendingOrders)
                .totalCapital(balance.getTotalBalance())
                .availableCapital(balance.getAvailableBalance())
                .maxOperationAmount(maxOperationAmount)
                .minOperationAmount(minimumOperationAmount)
                .maxOpenPositions(runner.getMaxOpenPositions())
                .currentOpenPositions(openLots.size())
                .realizedPnl(balance.getRealizedBalance())
                .unrealizedPnl(positionContext.unrealizedPnl())
                .build();
    }

    private OpenLotDto buildOpenLot(Position pos) {
        BigDecimal lockedQty = pos.getLockedQuantity() != null
                ? pos.getLockedQuantity() : BigDecimal.ZERO;
        BigDecimal available = pos.getQuantity().subtract(lockedQty).max(BigDecimal.ZERO);

        return OpenLotDto.builder()
                .lotId(pos.getId())
                .quantity(pos.getQuantity())
                .availableQuantity(available)
                .entryPrice(pos.getAveragePrice())
                .currentPnlPercent(calculatePnlPercent(pos))
                .openedAt(pos.getOpenedAt())
                .build();
    }

    private BigDecimal calculatePnlPercent(Position pos) {
        BigDecimal currentPrice = pos.getCurrentPrice();
        BigDecimal entryPrice = pos.getAveragePrice();
        if (currentPrice == null || entryPrice.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return currentPrice.subtract(entryPrice)
                .divide(entryPrice, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    private List<PendingOrderDto> buildPendingOrders(StrategyRunner runner) {
        return strategyRunnerRepository
                .findByRunnerIdAndStatuses(runner.getId(),
                        List.of(TransactionStatus.PENDING, TransactionStatus.SUBMITTED, TransactionStatus.PARTIAL))
                .stream()
                .map(t -> PendingOrderDto.builder()
                        .transactionId(t.getId())
                        .type(t.isBuy() ? TradingAction.SHOULD_BUY : TradingAction.SHOULD_SELL)
                        .quantity(t.getQuantity())
                        .price(t.getPrice())
                        .status(t.getStatus())
                        .requestedAt(t.getRequestedAt())
                        .build())
                .toList();
    }
}
