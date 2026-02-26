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
import com.marmitt.core.exceptions.ConcurrentPositionLockException;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Orquestrador principal do fluxo market data → decisao de trade → ordem na exchange.
 *
 * <p>A cada tick recebido, o use case:
 * <ol>
 *   <li>Localiza todos os {@link com.marmitt.core.domain.runner.StrategyRunner runners}
 *       operacionais para o simbolo/exchange do tick.</li>
 *   <li>Filtra runners inapta (status, exchange bloqueada) via {@link RunnerSignalPolicy}.</li>
 *   <li>Monta o {@link com.marmitt.core.dto.strategy.PortfolioContextDto contexto de portfolio}
 *       com saldo, posicoes abertas e ordens pendentes de venda.</li>
 *   <li>Executa a estrategia configurada no runner para obter a decisao (BUY/SELL/HOLD).</li>
 *   <li>Aplica guardas de execucao (politica SINGLE, capital) e materializa a
 *       {@link com.marmitt.core.domain.runner.Transaction transacao} local com status PENDING.</li>
 *   <li>Despacha a ordem para a exchange — o status so avanca de PENDING para SUBMITTED
 *       quando a exchange confirmar via callback assincono.</li>
 * </ol>
 *
 * <p><b>Fronteiras transacionais:</b> esta classe e abstrata; os metodos
 * {@code transactionalPersist*} devem ser implementados pela camada de composicao (Spring)
 * via {@code TransactionTemplate}. Isso mantem o dominio livre de dependencias de
 * infraestrutura enquanto garante atomicidade entre persistencia e reserva de capital.
 *
 * <p><b>Tratamento de erros por runner:</b> falhas em um runner individual sao capturadas
 * e logadas sem interromper o processamento dos demais runners do mesmo tick. Isso evita
 * que um runner com dados inconsistentes bloqueie todos os outros.
 *
 * @see RunnerSignalPolicy
 * @see CapitalReservationPolicy
 * @see BuySignalHandler
 * @see SellSignalHandler
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Secao 5.1 — ProcessTradeSignal</a>
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

    /**
     * Ponto de extensao para a fronteira transacional do ramo BUY.
     * Implementado pela camada de composicao para envolver {@link #persistBuyAndReserve}
     * em uma transacao de banco de dados.
     */
    public abstract void transactionalPersistBuyAndReserve(BuyExecutionContext context);

    /**
     * Ponto de extensao para a fronteira transacional do ramo SELL.
     * Implementado pela camada de composicao para envolver {@link #persistSellAndLockPosition}
     * em uma transacao de banco de dados.
     */
    public abstract void transactionalPersistSellAndLockPosition(Transaction transaction,
                                                                 Position targetPosition);

    /**
     * Corpo da persistencia BUY executado dentro da fronteira transacional.
     *
     * <p>Salva a transacao com status PENDING <em>antes</em> de tentar reservar capital.
     * Se a reserva falhar (saldo insuficiente, limite de exposicao, Safe Mode), a transacao
     * PENDING fica gravada mas o {@link BuySignalHandler} captura a excecao e descarta o sinal
     * — a transacao e entao tratada como intencao nao materializada e expirada pelo watchdog.
     *
     * <p>A persistencia previa garante rastreabilidade: se o processo cair apos o dispatch
     * mas antes do ACK da exchange, o sistema pode recuperar o estado pelo clientOrderId.
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
     * Entry point do caso de uso. Recebe um tick de market data e aciona o pipeline
     * completo para todos os runners operacionais do simbolo/exchange informados.
     * Cada runner e processado de forma independente — excecoes sao isoladas por runner.
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
            } catch (ConcurrentPositionLockException e) {
                log.warn("priceUpdate: concurrent SELL conflict for runner={} symbol={} - tick discarded (position already locked by another thread)",
                        runner.getId(), symbol);
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
