package com.marmitt.core.application.usecase.runner;

import com.marmitt.core.application.exception.CapitalReservationRejectedException;
import com.marmitt.core.domain.Symbol;
import com.marmitt.core.domain.portfolio.GlobalBalance;
import com.marmitt.core.domain.portfolio.Portfolio;
import com.marmitt.core.domain.runner.ClientOrderId;
import com.marmitt.core.domain.runner.Position;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.capital.CapitalRequest;
import com.marmitt.core.dto.runner.OrderDispatchCommand;
import com.marmitt.core.dto.strategy.OpenBuyEntryDto;
import com.marmitt.core.dto.strategy.PendingSellEntryDto;
import com.marmitt.core.dto.strategy.PortfolioContextDto;
import com.marmitt.core.dto.strategy.StrategyInputDto;
import com.marmitt.core.dto.strategy.StrategyOutputDto;
import com.marmitt.core.dto.websocket.data.MarketDataDto;
import com.marmitt.core.enums.ExecutionPolicy;
import com.marmitt.core.enums.RejectionReason;
import com.marmitt.core.enums.TradingAction;
import com.marmitt.core.enums.TransactionStatus;
import com.marmitt.core.enums.TransactionType;
import com.marmitt.core.ports.inbound.runner.ProcessTradeSignalPort;
import com.marmitt.core.ports.outbound.exchange.OrderDispatchPort;
import com.marmitt.core.ports.outbound.repository.GlobalBalanceRepositoryPort;
import com.marmitt.core.ports.outbound.repository.PortfolioRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import com.marmitt.core.ports.outbound.strategy.TradingStrategy;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * UseCase central do fluxo ProcessTradeSignal — recebe atualizações de preço e as roteia
 * para as estratégias de cada Runner ativo.
 * <p>
 * Implementa o protocolo Persist-First (IG Seção 6.2.1):
 * <ol>
 *   <li>Validar Runner e sinal</li>
 *   <li>Construir Transaction e CapitalRequest</li>
 *   <li>Localizar Position alvo (SELL)</li>
 *   <li>Persistir PENDING + reservar capital / lock Position — via métodos transacionais abstratos</li>
 *   <li>Despachar ordem para a exchange (fire-and-forget)</li>
 * </ol>
 * <p>
 * <b>Limites @Transactional:</b> os métodos abstratos {@link #transactionalPersistBuyAndReserve}
 * e {@link #transactionalPersistSellAndLockPosition} devem ser sobrescritos na camada
 * {@code spring-application} com {@code @Transactional} — ver {@code RunnerConfig}.
 *
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 6.2.1</a>
 */
@Slf4j
public abstract class ProcessTradeSignalUseCase implements ProcessTradeSignalPort {

    private static final BigDecimal MINIMUM_OPERATION_AMOUNT = BigDecimal.valueOf(10);

    private final StrategyRunnerRepositoryPort strategyRunnerRepository;
    private final StrategyRepositoryPort strategyRepository;
    private final GlobalBalanceRepositoryPort globalBalanceRepository;
    private final PortfolioRepositoryPort portfolioRepository;
    private final OrderDispatchPort orderDispatch;

    protected ProcessTradeSignalUseCase(StrategyRunnerRepositoryPort strategyRunnerRepository,
                                        StrategyRepositoryPort strategyRepository,
                                        GlobalBalanceRepositoryPort globalBalanceRepository,
                                        PortfolioRepositoryPort portfolioRepository,
                                        OrderDispatchPort orderDispatch) {

        this.strategyRunnerRepository = strategyRunnerRepository;
        this.strategyRepository = strategyRepository;
        this.globalBalanceRepository = globalBalanceRepository;
        this.portfolioRepository = portfolioRepository;
        this.orderDispatch = orderDispatch;
    }

    // ── Métodos abstratos transacionais ──────────────────────────────────────

    /**
     * Persiste a Transaction PENDING e reserva capital para uma ordem de compra.
     * <p>
     * Deve ser sobrescrito com {@code @Transactional(rollbackFor = CapitalReservationRejectedException.class)}
     * na camada Spring. A implementação concreta deve delegar a {@link #persistBuyAndReserve}.
     *
     * @throws CapitalReservationRejectedException se a reserva de capital falhar (aciona rollback)
     */
    public abstract void transactionalPersistBuyAndReserve(Transaction transaction,
                                                               CapitalRequest capitalRequest,
                                                               StrategyRunner runner);

    /**
     * Persiste a Transaction PENDING e aplica lock na Position alvo para uma ordem de venda.
     * <p>
     * Deve ser sobrescrito com {@code @Transactional} na camada Spring.
     * A implementação concreta deve delegar a {@link #persistSellAndLockPosition}.
     */
    public abstract void transactionalPersistSellAndLockPosition(Transaction transaction,
                                                                      Position targetPosition);

    // ── Lógica de domínio — chamada pelas subclasses Spring ──────────────────

    /**
     * Lógica de persistência BUY: salva a Transaction PENDING e reserva capital.
     * Deve ser chamado dentro de {@link #transactionalPersistBuyAndReserve}.
     * <p>
     * Sequência de validações (IG Seção 5.2.1):
     * <ol>
     *   <li>Portfolio SafeMode NORMAL → {@code RISK_VIOLATION}</li>
     *   <li>Limite de alocação do Runner não excedido → {@code RUNNER_LIMIT_EXCEEDED}</li>
     *   <li>Reserva atômica com lock pessimista → {@code INSUFFICIENT_FUNDS}</li>
     * </ol>
     * O runner já foi validado (canAcceptSignals) antes de chegar aqui.
     */
    public void persistBuyAndReserve(Transaction transaction, CapitalRequest capitalRequest, StrategyRunner runner) {
        strategyRunnerRepository.saveTransaction(transaction);

        // ── Passo 1: Portfolio SafeMode ────────────────────────────────────────
        Portfolio portfolio = portfolioRepository.findById(runner.getPortfolioId()).orElse(null);
        if (portfolio == null) {
            log.error("persistBuyAndReserve: portfolio {} not found for runner {}",
                    runner.getPortfolioId(), runner.getId());
            throw new CapitalReservationRejectedException(capitalRequest.transactionId(), RejectionReason.UNKNOWN_RUNNER);
        }
        if (portfolio.getSafeModeStatus().isActive()) {
            log.warn("persistBuyAndReserve: safe mode {} active for portfolio {}",
                    portfolio.getSafeModeStatus(), runner.getPortfolioId());
            throw new CapitalReservationRejectedException(capitalRequest.transactionId(), RejectionReason.RISK_VIOLATION);
        }

        // ── Passo 2: limite de alocação do Runner ──────────────────────────────
        GlobalBalance balance = globalBalanceRepository.findByPortfolioId(runner.getPortfolioId()).orElse(null);
        if (balance == null) {
            log.error("persistBuyAndReserve: global balance not found for portfolio {}",
                    runner.getPortfolioId());
            throw new CapitalReservationRejectedException(capitalRequest.transactionId(), RejectionReason.INSUFFICIENT_FUNDS);
        }

        BigDecimal currentExposure = calculateRunnerExposure(runner.getId());
        BigDecimal maxAllocation = runner.getMaxAllocationPercent().multiply(balance.getTotalBalance());
        if (currentExposure.add(capitalRequest.amount()).compareTo(maxAllocation) > 0) {
            log.warn("persistBuyAndReserve: RUNNER_LIMIT_EXCEEDED runner={} currentExposure={} requested={} maxAllocation={}",
                    runner.getId(), currentExposure, capitalRequest.amount(), maxAllocation);
            throw new CapitalReservationRejectedException(capitalRequest.transactionId(), RejectionReason.RUNNER_LIMIT_EXCEEDED);
        }

        // ── Passo 3: reserva atômica com lock pessimista ───────────────────────
        boolean reserved = globalBalanceRepository.reserveAtomic(runner.getPortfolioId(), capitalRequest.amount());
        if (!reserved) {
            log.warn("persistBuyAndReserve: INSUFFICIENT_FUNDS atomic reserve failed for portfolio={} amount={}",
                    runner.getPortfolioId(), capitalRequest.amount());
            throw new CapitalReservationRejectedException(capitalRequest.transactionId(), RejectionReason.INSUFFICIENT_FUNDS);
        }

        log.info("persistBuyAndReserve: capital reserved transactionId={} runnerId={} amount={} portfolioId={}",
                capitalRequest.transactionId(), runner.getId(), capitalRequest.amount(), runner.getPortfolioId());
    }

    /**
     * Lógica de persistência SELL: aplica lock na Position e salva Transaction + Position atomicamente.
     * Deve ser chamado dentro de {@link #transactionalPersistSellAndLockPosition}.
     */
    public void persistSellAndLockPosition(Transaction transaction, Position targetPosition) {
        targetPosition.lock(transaction.getId(), transaction.getQuantity());
        targetPosition.startClosing();
        strategyRunnerRepository.saveAtomicTransactionAndPositionLock(transaction, targetPosition);
    }

    // ── Fluxo principal ───────────────────────────────────────────────────────
    @Override
    public void execute(MarketDataDto marketData) {

        String symbol = marketData.symbol().value();
        String exchangeId = marketData.exchangeName();

        List<StrategyRunner> strategyRunners = strategyRunnerRepository.findOperationalBySymbol(symbol, exchangeId);

        if (strategyRunners.isEmpty()) {
            log.trace("priceUpdate: no operational runners for symbol={} exchange={}", symbol, exchangeId);
            return;
        }

        StrategyInputDto strategyInput = buildStrategyInput(marketData);

        for (StrategyRunner strategyRunner : strategyRunners) {
            if (!strategyRunner.canAcceptSignals()) {
                log.debug("priceUpdate: skipping runner={} — canAcceptSignals=false (status={} reconciling={})",
                        strategyRunner.getId(), strategyRunner.getStatus(), strategyRunner.isReconciling());
                continue;
            }

            if (!strategyRunner.canReceiveMarketDataFrom(exchangeId)) {
                log.debug("priceUpdate: skipping runner={} — exchange={} not in allowedSources", strategyRunner.getId(), exchangeId);
                continue;
            }

            try {
                processRunner(strategyRunner, strategyInput, marketData.price());
            } catch (Exception e) {
                log.error("priceUpdate: error processing runner={} symbol={} — {}", strategyRunner.getId(), symbol, e.getMessage(), e);
            }
        }

    }

    private void processRunner(StrategyRunner strategyRunner, StrategyInputDto strategyInput, BigDecimal currentPrice) {
        Optional<TradingStrategy> tradingStrategy = strategyRepository.findById(strategyRunner.getStrategyId())
                .or(() -> strategyRepository.findByName(strategyRunner.getStrategyName()));

        if (tradingStrategy.isEmpty()) {
            log.warn("priceUpdate: strategy not found for runner={} strategyId={} strategyName={}",
                    strategyRunner.getId(), strategyRunner.getStrategyId(), strategyRunner.getStrategyName());
            return;
        }

        boolean isStrategyEnable = tradingStrategy.map(TradingStrategy::isEnabled).orElse(false);
        if (!isStrategyEnable) {
            log.debug("priceUpdate: strategy disabled for runner={}", strategyRunner.getId());
            return;
        }

        PortfolioContextDto context = buildPortfolioContext(strategyRunner);

        StrategyOutputDto strategyOutputDto = tradingStrategy
                .map(ts -> ts.executeStrategy(strategyInput, context))
                .orElse(StrategyOutputDto.hold(strategyRunner.getStrategyName(), "Execution of strategy return null, SHOULD_HOLD by default."));

        if (strategyOutputDto.decision() == TradingAction.SHOULD_HOLD) {
            log.debug("priceUpdate: HOLD signal for runner={} — no action", strategyRunner.getId());
            return;
        }

        log.debug("priceUpdate: routing signal decision={} runner={} symbol={}",
                strategyOutputDto.decision(), strategyRunner.getId(), strategyRunner.getSymbol());

        processTradeSignal(strategyRunner, strategyOutputDto, currentPrice);
    }

    private void processTradeSignal(StrategyRunner runner, StrategyOutputDto signal, BigDecimal currentPrice) {
        if (!isRequestValid(runner, signal)) {
            return;
        }

        Transaction transaction = buildTransaction(runner, signal, currentPrice);

        if (transaction.getType() == TransactionType.BUY) {
            processBuySignal(runner, transaction);
        } else {
            processSellSignal(runner, signal, transaction);
        }
    }

    private void processBuySignal(StrategyRunner runner, Transaction transaction) {
        CapitalRequest capitalRequest = new CapitalRequest(
                transaction.getId(),
                runner.getId(),
                runner.getShortCode(),
                runner.getSymbol(),
                transaction.getTotal(),
                transaction.getType()
        );

        try {
            transactionalPersistBuyAndReserve(transaction, capitalRequest, runner);
        } catch (CapitalReservationRejectedException ex) {
            log.warn("processBuySignal: signal discarded — capital rejected transactionId={} reason={}",
                    ex.getTransactionId(), ex.getReason());
            return;
        }

        dispatch(runner, transaction);
    }

    private void processSellSignal(StrategyRunner runner, StrategyOutputDto signal, Transaction transaction) {
        Optional<Position> targetPosition = findTargetPosition(runner, signal);
        if (targetPosition.isEmpty()) {
            log.warn("processSellSignal: SELL signal discarded — no open position for runner={} symbol={}",
                    runner.getId(), runner.getSymbol());
            return;
        }

        transactionalPersistSellAndLockPosition(transaction, targetPosition.get());
        dispatch(runner, transaction);
    }

    private void dispatch(StrategyRunner runner, Transaction transaction) {
        OrderDispatchCommand command = new OrderDispatchCommand(
                transaction.getClientOrderId(),
                runner.getId(),
                runner.getSymbol(),
                runner.getExchangeId(),
                transaction.getType(),
                transaction.getQuantity(),
                transaction.getPrice()
        );

        orderDispatch.dispatch(command);
        log.debug("dispatch: order sent — clientOrderId={} stays PENDING until exchange confirms",
                transaction.getClientOrderId());
    }

    // ── Validação ─────────────────────────────────────────────────────────────

    private boolean isRequestValid(StrategyRunner runner, StrategyOutputDto signal) {
        if (!runner.canAcceptSignals()) {
            log.warn("processTradeSignal: runner={} cannot accept signals (status={} reconciling={})",
                    runner.getId(), runner.getStatus(), runner.isReconciling());
            return false;
        }

        if (signal.decision() == TradingAction.SHOULD_HOLD) {
            log.debug("processTradeSignal: HOLD signal discarded for runner={}", runner.getId());
            return false;
        }

        if (runner.getExecutionPolicy() == ExecutionPolicy.SINGLE) {
            if (signal.decision() == TradingAction.SHOULD_BUY) {
                if (hasOpenPositionOrInFlight(runner)) {
                    log.info("processTradeSignal: BUY signal rejected by SINGLE policy — " +
                                    "open position or in-flight orders exist for runner={}",
                            runner.getId());
                    return false;
                }
            }
        }

        return true;
    }

    private BigDecimal calculateRunnerExposure(UUID runnerId) {
        List<Transaction> inflight = strategyRunnerRepository.findByRunnerIdAndStatuses(runnerId, List.of(
                TransactionStatus.PENDING,
                TransactionStatus.SUBMITTED,
                TransactionStatus.PARTIAL
        ));
        return inflight.stream()
                .map(Transaction::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean hasOpenPositionOrInFlight(StrategyRunner runner) {
        List<Position> openPositions = strategyRunnerRepository.findOpenPositionsByRunnerId(runner.getId());
        if (!openPositions.isEmpty()) return true;

        List<Transaction> inFlight =
                strategyRunnerRepository.findByRunnerIdAndStatuses(runner.getId(), List.of(
                        com.marmitt.core.enums.TransactionStatus.PENDING,
                        com.marmitt.core.enums.TransactionStatus.SUBMITTED,
                        com.marmitt.core.enums.TransactionStatus.PARTIAL
                ));
        return !inFlight.isEmpty();
    }

    private Optional<Position> findTargetPosition(StrategyRunner runner, StrategyOutputDto signal) {
        if (signal.targetLotId() != null) {
            return strategyRunnerRepository.findPositionById(signal.targetLotId());
        }
        return strategyRunnerRepository.findOpenPositionByRunnerIdAndSymbol(runner.getId(), runner.getSymbol());
    }

    // ── Builders ──────────────────────────────────────────────────────────────

    private Transaction buildTransaction(StrategyRunner runner, StrategyOutputDto signal, BigDecimal currentPrice) {
        TransactionType type = signal.decision() == TradingAction.SHOULD_BUY ? TransactionType.BUY : TransactionType.SELL;
        String clientOrderId = ClientOrderId.generate(runner.getShortCode(), type);
        BigDecimal total = signal.quantity()
                .multiply(currentPrice)
                .setScale(8, RoundingMode.HALF_UP);

        return new Transaction(
                runner.getId(),
                clientOrderId,
                type,
                runner.getSymbol(),
                signal.quantity(),
                currentPrice,
                total,
                signal.confidence(),
                signal.reasoning(),
                signal.targetLotId()
        );
    }

    private PortfolioContextDto buildPortfolioContext(StrategyRunner runner) {
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
                .minimumOperationAmount(MINIMUM_OPERATION_AMOUNT)
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

    private StrategyInputDto buildStrategyInput(MarketDataDto marketData) {
        return StrategyInputDto.builder()
                .symbol(marketData.symbol())
                .currentPrice(marketData.price())
                .bidPrice(marketData.bidPrice())
                .askPrice(marketData.askPrice())
                .volume(marketData.volume())
                .high24h(marketData.high24h())
                .low24h(marketData.low24h())
                .timestamp(marketData.timestamp())
                .build();
    }
}
