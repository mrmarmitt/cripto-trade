package com.marmitt.core.application.usecase.runner.processsignal;

import com.marmitt.core.domain.runner.Position;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.capital.BuyExecutionContext;
import com.marmitt.core.dto.strategy.PortfolioContextDto;
import com.marmitt.core.dto.strategy.StrategyInputDto;
import com.marmitt.core.dto.strategy.StrategyOutputDto;
import com.marmitt.core.dto.websocket.data.MarketDataDto;
import com.marmitt.core.ports.inbound.runner.ProcessTradeSignalPort;
import com.marmitt.core.ports.outbound.strategy.TradingStrategy;
import com.marmitt.core.ports.outbound.exchange.OrderDispatchPort;
import com.marmitt.core.ports.outbound.repository.GlobalBalanceRepositoryPort;
import com.marmitt.core.ports.outbound.repository.PortfolioRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Caso de uso principal para processamento de sinais de trade por market data.
 *
 * Responsabilidades:
 * - Rotear ticks para runners elegiveis.
 * - Montar contexto e executar estrategia.
 * - Materializar intencao de BUY/SELL.
 * - Delegar validacoes e persistencia para colaboradores especializados.
 *
 * Observacao:
 * As fronteiras transacionais continuam na camada de composicao (Spring),
 * via metodos abstratos {@code transactional*}.
 */
@Slf4j
public abstract class ProcessTradeSignalUseCase implements ProcessTradeSignalPort {

    private static final BigDecimal MINIMUM_OPERATION_AMOUNT = BigDecimal.valueOf(10);

    private final StrategyRunnerRepositoryPort strategyRunnerRepository;

    private final RunnerSignalPolicy signalPolicy;
    private final StrategySignalEvaluator signalEvaluator;
    private final RunnerContextAssembler contextAssembler;
    private final TradeIntentFactory tradeIntentFactory;
    private final BuySignalHandler buySignalHandler;
    private final SellSignalHandler sellSignalHandler;
    private final RunnerExposureService runnerExposureService;
    private final CapitalReservationPolicy capitalReservationPolicy;

    protected ProcessTradeSignalUseCase(StrategyRunnerRepositoryPort strategyRunnerRepository,
                                        StrategyRepositoryPort strategyRepository,
                                        GlobalBalanceRepositoryPort globalBalanceRepository,
                                        PortfolioRepositoryPort portfolioRepository,
                                        OrderDispatchPort orderDispatch) {
        this.strategyRunnerRepository = strategyRunnerRepository;

        this.signalPolicy = new RunnerSignalPolicy();
        this.signalEvaluator = new StrategySignalEvaluator(strategyRepository);
        this.contextAssembler = new RunnerContextAssembler(
                globalBalanceRepository, strategyRunnerRepository, MINIMUM_OPERATION_AMOUNT);
        this.tradeIntentFactory = new TradeIntentFactory();
        this.buySignalHandler = new BuySignalHandler(this.tradeIntentFactory, orderDispatch);
        this.sellSignalHandler = new SellSignalHandler(strategyRunnerRepository, this.tradeIntentFactory, orderDispatch);
        this.runnerExposureService = new RunnerExposureService(strategyRunnerRepository);
        this.capitalReservationPolicy = new CapitalReservationPolicy(
                portfolioRepository, globalBalanceRepository, this.runnerExposureService);
    }

    public abstract void transactionalPersistBuyAndReserve(BuyExecutionContext context);

    public abstract void transactionalPersistSellAndLockPosition(Transaction transaction,
                                                                 Position targetPosition);

    /**
     * Fluxo de persistencia para BUY dentro de uma fronteira transacional externa.
     * Salva a transacao PENDING e delega validacao/reserva para a politica de capital.
     */
    protected void persistBuyAndReserve(BuyExecutionContext context) {
        strategyRunnerRepository.saveTransaction(context.transaction());
        capitalReservationPolicy.validateAndReserve(
                context.capitalRequest(), context.runner(), context.precomputedExposure());

        log.info("persistBuyAndReserve: capital reserved transactionId={} runnerId={} amount={} portfolioId={}",
                context.capitalRequest().transactionId(),
                context.runner().getId(),
                context.capitalRequest().amount(),
                context.runner().getPortfolioId());
    }

    protected void persistSellAndLockPosition(Transaction transaction, Position targetPosition) {
        targetPosition.lock(transaction.getId(), transaction.getQuantity());
        targetPosition.startClosing();
        strategyRunnerRepository.saveAtomicTransactionAndPositionLock(transaction, targetPosition);
    }

    /**
     * Entry point do caso de uso.
     * Recebe market data e processa runners operacionais para simbolo/exchange.
     */
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
        Optional<TradingStrategy> activeStrategy = signalEvaluator.resolveActiveStrategy(runner);
        if (activeStrategy.isEmpty()) {
            return;
        }

        PortfolioContextDto context = contextAssembler.assemble(runner);
        StrategyOutputDto strategyOutput = signalEvaluator.evaluate(runner, activeStrategy.get(), input, context);
        if (signalEvaluator.isHold(strategyOutput)) {
            log.debug("priceUpdate: HOLD signal for runner={} - no action", runner.getId());
            return;
        }

        log.debug("priceUpdate: routing signal decision={} runner={} symbol={}",
                strategyOutput.decision(), runner.getId(), runner.getSymbol());

        processTradeSignal(runner, strategyOutput, currentPrice);
    }

    private void processTradeSignal(StrategyRunner runner, StrategyOutputDto signal, BigDecimal currentPrice) {
        RunnerExposureService.ExposureSnapshot exposureSnapshot = null;
        boolean hasOpenOrInflight = false;
        if (signalPolicy.requiresOpenPositionCheck(runner, signal)) {
            exposureSnapshot = runnerExposureService.loadSnapshot(runner);
            hasOpenOrInflight = exposureSnapshot.hasOpenOrInFlight();
        }
        if (!signalPolicy.canExecuteSignal(runner, signal, hasOpenOrInflight)) {
            return;
        }

        Transaction transaction = tradeIntentFactory.buildTransaction(runner, signal, currentPrice);
        if (transaction.isBuy()) {
            BigDecimal precomputedExposure = exposureSnapshot != null
                    ? exposureSnapshot.inFlightExposure()
                    : null;
            BuyExecutionContext buyContext = tradeIntentFactory
                    .buildBuyExecutionContext(runner, transaction, precomputedExposure);
            buySignalHandler.handle(buyContext, this::transactionalPersistBuyAndReserve);
        } else {
            sellSignalHandler.handle(runner, signal, transaction, this::transactionalPersistSellAndLockPosition);
        }
    }

}
