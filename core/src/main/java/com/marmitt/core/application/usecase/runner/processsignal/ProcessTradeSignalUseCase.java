package com.marmitt.core.application.usecase.runner.processsignal;

import com.marmitt.core.application.exception.CapitalReservationRejectedException;
import com.marmitt.core.domain.portfolio.GlobalBalance;
import com.marmitt.core.domain.portfolio.Portfolio;
import com.marmitt.core.domain.runner.Position;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.capital.CapitalRequest;
import com.marmitt.core.dto.strategy.PortfolioContextDto;
import com.marmitt.core.dto.strategy.StrategyInputDto;
import com.marmitt.core.dto.strategy.StrategyOutputDto;
import com.marmitt.core.dto.websocket.data.MarketDataDto;
import com.marmitt.core.enums.RejectionReason;
import com.marmitt.core.enums.TransactionStatus;
import com.marmitt.core.ports.inbound.runner.ProcessTradeSignalPort;
import com.marmitt.core.ports.outbound.exchange.OrderDispatchPort;
import com.marmitt.core.ports.outbound.repository.GlobalBalanceRepositoryPort;
import com.marmitt.core.ports.outbound.repository.PortfolioRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Versao refatorada de estudo para o ProcessTradeSignal:
 * - use case continua como unico entrypoint
 * - responsabilidades internas divididas em colaboradores package-level.
 */
@Slf4j
public abstract class ProcessTradeSignalUseCase implements ProcessTradeSignalPort {

    private static final BigDecimal MINIMUM_OPERATION_AMOUNT = BigDecimal.valueOf(10);

    private final StrategyRunnerRepositoryPort strategyRunnerRepository;
    private final GlobalBalanceRepositoryPort globalBalanceRepository;
    private final PortfolioRepositoryPort portfolioRepository;

    private final RunnerSignalPolicy signalPolicy;
    private final StrategySignalEvaluator signalEvaluator;
    private final RunnerContextAssembler contextAssembler;
    private final TradeIntentFactory tradeIntentFactory;
    private final BuySignalHandler buySignalHandler;
    private final SellSignalHandler sellSignalHandler;

    protected ProcessTradeSignalUseCase(StrategyRunnerRepositoryPort strategyRunnerRepository,
                                        StrategyRepositoryPort strategyRepository,
                                        GlobalBalanceRepositoryPort globalBalanceRepository,
                                        PortfolioRepositoryPort portfolioRepository,
                                        OrderDispatchPort orderDispatch) {
        this.strategyRunnerRepository = strategyRunnerRepository;
        this.globalBalanceRepository = globalBalanceRepository;
        this.portfolioRepository = portfolioRepository;

        this.signalPolicy = new RunnerSignalPolicy();
        this.signalEvaluator = new StrategySignalEvaluator(strategyRepository);
        this.contextAssembler = new RunnerContextAssembler(
                globalBalanceRepository, strategyRunnerRepository, MINIMUM_OPERATION_AMOUNT);
        this.tradeIntentFactory = new TradeIntentFactory();
        this.buySignalHandler = new BuySignalHandler(this.tradeIntentFactory, orderDispatch);
        this.sellSignalHandler = new SellSignalHandler(strategyRunnerRepository, this.tradeIntentFactory, orderDispatch);
    }

    public abstract void transactionalPersistBuyAndReserve(Transaction transaction,
                                                           CapitalRequest capitalRequest,
                                                           StrategyRunner runner);

    public abstract void transactionalPersistSellAndLockPosition(Transaction transaction,
                                                                 Position targetPosition);

    protected void persistBuyAndReserve(Transaction transaction, CapitalRequest capitalRequest, StrategyRunner runner) {
        strategyRunnerRepository.saveTransaction(transaction);

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

        boolean reserved = globalBalanceRepository.reserveAtomic(runner.getPortfolioId(), capitalRequest.amount());
        if (!reserved) {
            log.warn("persistBuyAndReserve: INSUFFICIENT_FUNDS atomic reserve failed for portfolio={} amount={}",
                    runner.getPortfolioId(), capitalRequest.amount());
            throw new CapitalReservationRejectedException(capitalRequest.transactionId(), RejectionReason.INSUFFICIENT_FUNDS);
        }

        log.info("persistBuyAndReserve: capital reserved transactionId={} runnerId={} amount={} portfolioId={}",
                capitalRequest.transactionId(), runner.getId(), capitalRequest.amount(), runner.getPortfolioId());
    }

    protected void persistSellAndLockPosition(Transaction transaction, Position targetPosition) {
        targetPosition.lock(transaction.getId(), transaction.getQuantity());
        targetPosition.startClosing();
        strategyRunnerRepository.saveAtomicTransactionAndPositionLock(transaction, targetPosition);
    }

    @Override
    public void execute(MarketDataDto marketData) {
        String symbol = marketData.symbol().value();
        String exchangeId = marketData.exchangeName();

        List<StrategyRunner> strategyRunners = strategyRunnerRepository.findOperationalBySymbol(symbol, exchangeId);
        if (strategyRunners.isEmpty()) {
            log.trace("priceUpdate: no operational runners for symbol={} exchange={}", symbol, exchangeId);
            return;
        }

        StrategyInputDto strategyInput = tradeIntentFactory.buildStrategyInput(marketData);
        for (StrategyRunner runner : strategyRunners) {
            if (!signalPolicy.canProcessRunner(runner, exchangeId)) {
                continue;
            }

            try {
                processRunner(runner, strategyInput, marketData.price());
            } catch (Exception e) {
                log.error("priceUpdate: error processing runner={} symbol={} - {}",
                        runner.getId(), symbol, e.getMessage(), e);
            }
        }
    }

    private void processRunner(StrategyRunner runner, StrategyInputDto input, BigDecimal currentPrice) {
        PortfolioContextDto context = contextAssembler.assemble(runner);
        StrategyOutputDto strategyOutput = signalEvaluator.evaluate(runner, input, context);
        if (signalEvaluator.isHold(strategyOutput)) {
            log.debug("priceUpdate: HOLD signal for runner={} - no action", runner.getId());
            return;
        }

        log.debug("priceUpdate: routing signal decision={} runner={} symbol={}",
                strategyOutput.decision(), runner.getId(), runner.getSymbol());

        processTradeSignal(runner, strategyOutput, currentPrice);
    }

    private void processTradeSignal(StrategyRunner runner, StrategyOutputDto signal, BigDecimal currentPrice) {
        boolean hasOpenOrInflight = hasOpenPositionOrInflight(runner);
        if (!signalPolicy.canExecuteSignal(runner, signal, hasOpenOrInflight)) {
            return;
        }

        Transaction transaction = tradeIntentFactory.buildTransaction(runner, signal, currentPrice);
        if (transaction.isBuy()) {
            buySignalHandler.handle(runner, transaction, this::transactionalPersistBuyAndReserve);
        } else {
            sellSignalHandler.handle(runner, signal, transaction, this::transactionalPersistSellAndLockPosition);
        }
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

    private boolean hasOpenPositionOrInflight(StrategyRunner runner) {
        List<Position> openPositions = strategyRunnerRepository.findOpenPositionsByRunnerId(runner.getId());
        if (!openPositions.isEmpty()) {
            return true;
        }

        List<Transaction> inFlight = strategyRunnerRepository.findByRunnerIdAndStatuses(runner.getId(), List.of(
                TransactionStatus.PENDING,
                TransactionStatus.SUBMITTED,
                TransactionStatus.PARTIAL
        ));
        return !inFlight.isEmpty();
    }
}
