package com.marmitt.core.application.usecase.runner.processsignal;

import com.marmitt.core.domain.portfolio.Portfolio;
import com.marmitt.core.domain.runner.Position;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.capital.BuyExecutionContext;
import com.marmitt.core.dto.strategy.StrategyContextDto;
import com.marmitt.core.dto.strategy.StrategyInputDto;
import com.marmitt.core.dto.strategy.StrategyOutputDto;
import com.marmitt.core.dto.websocket.data.MarketDataDto;
import com.marmitt.core.domain.Symbol;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.enums.OrderType;
import com.marmitt.core.enums.SafeModeStatus;
import com.marmitt.core.ports.inbound.runner.OrderConciliationPort;
import com.marmitt.core.ports.inbound.runner.ProcessTradeSignalPort;
import com.marmitt.core.ports.inbound.runner.RunnerOrderCommandPort;
import com.marmitt.core.ports.outbound.strategy.TradingStrategy;
import com.marmitt.core.ports.outbound.exchange.OrderDispatchPort;
import com.marmitt.core.ports.outbound.exchange.rest.OrderQuantityNormalizerPort;
import com.marmitt.core.ports.outbound.marketdata.LatestPricePort;
import com.marmitt.core.ports.outbound.metrics.SignalDecision;
import com.marmitt.core.ports.outbound.metrics.SignalMetricsPort;
import com.marmitt.core.ports.outbound.metrics.TickDiscardReason;
import com.marmitt.core.ports.outbound.repository.GlobalBalanceRepositoryPort;
import com.marmitt.core.ports.outbound.repository.PortfolioRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import com.marmitt.core.exceptions.ConcurrentPositionLockException;
import com.marmitt.core.exceptions.RunnerHaltedException;
import com.marmitt.core.exceptions.RunnerNotFoundException;
import com.marmitt.core.exceptions.RunnerOperationException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Predicate;

/**
 * Orquestrador principal do fluxo market data → decisao de trade → ordem na exchange.
 *
 * <p>A cada tick recebido, o use case:
 * <ol>
 *   <li>Localiza todos os {@link com.marmitt.core.domain.runner.StrategyRunner runners}
 *       operacionais para o simbolo/exchange do tick.</li>
 *   <li>Filtra runners inapta (status, exchange bloqueada) via {@link RunnerSignalPolicy}.</li>
 *   <li>Monta o {@link com.marmitt.core.dto.strategy.StrategyContextDto contexto operacional}
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
public abstract class ProcessTradeSignalUseCase implements ProcessTradeSignalPort, RunnerOrderCommandPort {

    private static final BigDecimal MINIMUM_OPERATION_AMOUNT = BigDecimal.valueOf(10);

    private final StrategyRunnerRepositoryPort strategyRunnerRepository;
    private final PortfolioRepositoryPort portfolioRepository;
    private final OrderConciliationPort orderConciliation;
    private final SignalMetricsPort signalMetrics;
    private final LatestPricePort latestPrices;
    private final RunnerMailbox mailbox;
    private final Duration staleTickThreshold;
    private final Duration slowProcessingWarning;

    private final RunnerSignalPolicy signalPolicy;
    private final StrategySignalEvaluator signalEvaluator;
    private final RunnerContextAssembler contextAssembler;
    private final TradeIntentFactory tradeIntentFactory;
    private final OrderNormalizer orderNormalizer;
    private final BuySignalHandler buySignalHandler;
    private final SellSignalHandler sellSignalHandler;
    private final CancelSignalHandler cancelSignalHandler;
    private final SafeModeHandler safeModeHandler;
    private final RunnerExposureService runnerExposureService;
    private final CapitalReservationPolicy capitalReservationPolicy;

    protected ProcessTradeSignalUseCase(StrategyRunnerRepositoryPort strategyRunnerRepository,
                                        StrategyRepositoryPort strategyRepository,
                                        GlobalBalanceRepositoryPort globalBalanceRepository,
                                        PortfolioRepositoryPort portfolioRepository,
                                        OrderDispatchPort orderDispatch,
                                        OrderConciliationPort orderConciliation,
                                        List<OrderQuantityNormalizerPort> orderNormalizers,
                                        SignalMetricsPort signalMetrics,
                                        LatestPricePort latestPrices,
                                        Executor runnerExecutor,
                                        Duration staleTickThreshold,
                                        Duration slowProcessingWarning) {
        this.strategyRunnerRepository = strategyRunnerRepository;
        this.portfolioRepository = portfolioRepository;
        this.orderConciliation = orderConciliation;
        this.signalMetrics = signalMetrics;
        this.latestPrices = latestPrices;
        this.mailbox = new RunnerMailbox(runnerExecutor, this::evaluateTick, signalMetrics);
        this.staleTickThreshold = staleTickThreshold;
        this.slowProcessingWarning = slowProcessingWarning;

        this.signalPolicy = new RunnerSignalPolicy();
        this.signalEvaluator = new StrategySignalEvaluator(strategyRepository);
        this.contextAssembler = new RunnerContextAssembler(
                globalBalanceRepository, strategyRunnerRepository, MINIMUM_OPERATION_AMOUNT);
        this.tradeIntentFactory = new TradeIntentFactory();
        this.orderNormalizer = new OrderNormalizer(orderNormalizers);
        this.buySignalHandler = new BuySignalHandler(this.tradeIntentFactory, orderDispatch);
        this.sellSignalHandler = new SellSignalHandler(strategyRunnerRepository, this.tradeIntentFactory, orderDispatch);
        this.cancelSignalHandler = new CancelSignalHandler(strategyRunnerRepository, this.tradeIntentFactory, orderDispatch);
        this.safeModeHandler = new SafeModeHandler(strategyRunnerRepository, this.tradeIntentFactory, orderDispatch);
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
     * <p>Salva a transacao com status PENDING e reserva o capital na mesma transacao de banco.
     * Se a reserva falhar (saldo insuficiente, limite de exposicao, Safe Mode), a excecao faz
     * rollback de tudo — nada fica gravado — e o {@link BuySignalHandler} descarta o sinal.
     *
     * <p>A persistencia previa garante rastreabilidade: se o processo cair apos o dispatch
     * mas antes do ACK da exchange, o sistema pode recuperar o estado pelo clientOrderId.
     */
    protected void persistBuyAndReserve(BuyExecutionContext context) {
        // Re-read runner inside the transaction to close the race with the kill switch.
        // PostgreSQL READ COMMITTED ensures this sees any HALTED status committed since the signal was loaded.
        requireRunner(context.runner().getId(), StrategyRunner::canOpenPositions);

        strategyRunnerRepository.saveTransaction(context.transaction());
        capitalReservationPolicy.validateAndReserve(
                context.capitalRequest(), context.runner(), context.precomputedExposure());

        log.info("persistBuyAndReserve: capital reserved transactionId={} runnerId={} amount={} portfolioId={}",
                context.capitalRequest().transactionId(),
                context.runner().getId(),
                context.capitalRequest().amount(),
                context.runner().getPortfolioId());
    }

    private void expirePendingSell(Transaction tx) {
        OrderDataDto rejected = new OrderDataDto(
                null,
                tx.getClientOrderId(),
                Symbol.of(tx.getSymbol()),
                OrderDataDto.OrderSide.SELL,
                OrderDataDto.OrderType.LIMIT,
                tx.getQuantity(),
                BigDecimal.ZERO,
                tx.getPrice(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                OrderDataDto.OrderStatus.REJECTED,
                "Runner halted mid-flight — signal expired before dispatch",
                Instant.now(),
                null
        );
        orderConciliation.execute(rejected);
    }

    private void expirePendingBuy(BuyExecutionContext context) {
        Transaction tx = context.transaction();
        OrderDataDto rejected = new OrderDataDto(
                null,
                tx.getClientOrderId(),
                Symbol.of(tx.getSymbol()),
                OrderDataDto.OrderSide.BUY,
                OrderDataDto.OrderType.LIMIT,
                tx.getQuantity(),
                BigDecimal.ZERO,
                tx.getPrice(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                OrderDataDto.OrderStatus.REJECTED,
                "Runner halted mid-flight — signal expired before dispatch",
                Instant.now(),
                null
        );
        orderConciliation.execute(rejected);
    }

    private void checkRunnerNotHalted(UUID runnerId) {
        requireRunner(runnerId, StrategyRunner::canAcceptSignals);
    }

    private void checkRunnerCanOpen(UUID runnerId) {
        requireRunner(runnerId, StrategyRunner::canOpenPositions);
    }

    /**
     * Rele o runner (fecha a corrida com kill switch, encerramento e reconciliacao) e exige a condicao.
     * PostgreSQL READ COMMITTED garante que a releitura ve o status commitado desde o inicio do tick.
     */
    private void requireRunner(UUID runnerId, Predicate<StrategyRunner> condition) {
        StrategyRunner current = strategyRunnerRepository.findById(runnerId)
                .orElseThrow(() -> new IllegalStateException("Runner disappeared mid-signal: " + runnerId));
        if (!condition.test(current)) {
            throw new RunnerHaltedException(runnerId);
        }
    }

    protected void persistSellAndLockPosition(Transaction transaction, Position targetPosition) {
        requireRunner(transaction.getRunnerId(), StrategyRunner::canAcceptSignals);

        strategyRunnerRepository.saveTransaction(transaction);
        boolean locked = strategyRunnerRepository.tryLockPositionForSell(
                targetPosition.getId(),
                transaction.getId(),
                transaction.getQuantity()
        );
        if (!locked) {
            throw new ConcurrentPositionLockException(targetPosition.getId(), transaction.getRunnerId());
        }
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

        // T42: so roteia. A avaliacao de cada runner acontece na mailbox dele (latest-wins, sequencial),
        // no executor dos runners — a thread de market data nunca avalia estrategia.
        for (StrategyRunner runner : strategyRunners) {
            if (signalPolicy.canProcessRunner(runner, exchangeId)) {
                mailbox.submit(runner.getId(), marketData);
            }
        }
    }

    /**
     * Avalia o tick mais recente de um runner, ja fora da thread de market data. Rele o runner (o
     * status pode ter mudado enquanto o tick esperava: kill switch, encerramento, reconciliacao) e
     * descarta o tick que envelheceu na fila alem de {@code staleTickThreshold} (IG §12.3).
     */
    private void evaluateTick(UUID runnerId, MarketDataDto marketData) {
        Duration age = Duration.between(marketData.timestamp(), Instant.now());
        if (age.compareTo(staleTickThreshold) > 0) {
            signalMetrics.recordTickDiscarded(runnerId, TickDiscardReason.STALE);
            log.warn("priceUpdate: stale tick discarded runnerId={} age={}ms threshold={}ms",
                    runnerId, age.toMillis(), staleTickThreshold.toMillis());
            return;
        }
        StrategyRunner runner = strategyRunnerRepository.findById(runnerId).orElse(null);
        if (runner == null || !signalPolicy.canProcessRunner(runner, marketData.exchangeName())) {
            return;
        }

        long start = System.nanoTime();
        try {
            SafeModeStatus safeMode = loadSafeMode(runner.getPortfolioId());
            if (safeMode.cancelsOrders()) {
                enforceSafeMode(runner, safeMode, marketData.price());
            } else {
                processRunner(runner, tradeIntentFactory.buildStrategyInput(marketData), marketData.price());
            }
        } catch (RunnerHaltedException e) {
            log.warn("priceUpdate: signal discarded - runner halted mid-flight runnerId={} symbol={}",
                    runnerId, runner.getSymbol());
        } catch (ConcurrentPositionLockException e) {
            signalMetrics.recordSignalEvaluated(runnerId, SignalDecision.REJECTED_LOCK);
            log.warn("priceUpdate: concurrent SELL conflict for runner={} symbol={} - tick discarded (position already locked by another thread)",
                    runnerId, runner.getSymbol());
        } catch (Exception e) {
            log.error("priceUpdate: error processing runner={} symbol={} - {}",
                    runnerId, runner.getSymbol(), e.getMessage(), e);
        } finally {
            Duration took = Duration.ofNanos(System.nanoTime() - start);
            signalMetrics.recordProcessingTime(runnerId, took);
            if (took.compareTo(slowProcessingWarning) > 0) {
                log.warn("priceUpdate: slow evaluation runnerId={} took={}ms warnAbove={}ms",
                        runnerId, took.toMillis(), slowProcessingWarning.toMillis());
            }
        }
    }

    /**
     * Nivel do Safe Mode do portfolio do runner, lido a cada avaliacao. Sem portfolio, NORMAL: o BUY
     * ja e recusado pela politica de capital.
     */
    private SafeModeStatus loadSafeMode(UUID portfolioId) {
        return portfolioRepository.findById(portfolioId)
                .map(Portfolio::getSafeModeStatus)
                .orElse(SafeModeStatus.NORMAL);
    }

    /**
     * Niveis CANCEL_ALL e PANIC_SELL (T45): o Safe Mode conduz o runner no lugar da estrategia.
     * Cancela as ordens LIMIT em voo e, em PANIC_SELL, vende a mercado cada posicao aberta sem lock
     * pelo pipeline normal de SELL. Reaplicado a cada tick ate nao restar ordem nem posicao.
     */
    private void enforceSafeMode(StrategyRunner runner, SafeModeStatus safeMode, BigDecimal currentPrice) {
        checkRunnerNotHalted(runner.getId());
        signalMetrics.recordSignalEvaluated(runner.getId(), SignalDecision.SAFE_MODE);
        safeModeHandler.cancelRestingOrders(runner);
        if (!safeMode.liquidatesPositions()) {
            return;
        }
        for (StrategyOutputDto liquidation : safeModeHandler.liquidationSignals(runner)) {
            log.warn("safeMode: PANIC_SELL liquidating positionId={} runnerId={} quantity={}",
                    liquidation.targetLotId(), runner.getId(), liquidation.quantity());
            processTradeSignal(runner, liquidation, currentPrice, safeModeHandler.liquidationOrderType());
        }
    }

    // ============================================================
    // Comandos do operador (T49)
    // ============================================================

    @Override
    public void cancelOrder(UUID runnerId, UUID transactionId) {
        StrategyRunner runner = loadRunner(runnerId);
        switch (cancelSignalHandler.handle(runner, transactionId, "admin FORCE_CANCEL")) {
            case DISPATCHED -> { }
            case NOT_FOUND -> throw new RunnerOperationException(
                    "Order " + transactionId + " not found for runner " + runnerId);
            case NOT_CANCELABLE -> throw new RunnerOperationException(
                    "Order " + transactionId + " is not in flight");
        }
    }

    @Override
    public void closePosition(UUID runnerId, UUID positionId) {
        StrategyRunner runner = loadRunner(runnerId);
        if (!runner.canAcceptSignals()) {
            throw new RunnerOperationException("Runner " + runnerId + " in status " + runner.getStatus()
                    + (runner.isReconciling() ? " (reconciling)" : "") + " cannot sell - resume it first");
        }
        Position position = strategyRunnerRepository.findPositionById(positionId)
                .filter(found -> found.getRunnerId().equals(runnerId) && found.isOpen())
                .orElseThrow(() -> new RunnerOperationException(
                        "Position " + positionId + " is not open for runner " + runnerId));
        if (position.isLocked()) {
            throw new RunnerOperationException("Position " + positionId
                    + " is locked by an in-flight SELL - cancel that order first");
        }
        BigDecimal referencePrice = latestPrices.find(runner.getSymbol())
                .orElseThrow(() -> new RunnerOperationException(
                        "No market price yet for " + runner.getSymbol() + " - wait for the first tick"));
        log.warn("adminCommand: FORCE_CLOSE_POSITION positionId={} runnerId={} quantity={} referencePrice={}",
                positionId, runnerId, position.getQuantity(), referencePrice);
        processTradeSignal(runner, safeModeHandler.liquidationSignal(position, "admin FORCE_CLOSE_POSITION"),
                referencePrice, safeModeHandler.liquidationOrderType());
    }

    private StrategyRunner loadRunner(UUID runnerId) {
        return strategyRunnerRepository.findById(runnerId)
                .orElseThrow(() -> new RunnerNotFoundException(runnerId));
    }

    private void processRunner(StrategyRunner runner, StrategyInputDto input, BigDecimal currentPrice) {
        Optional<TradingStrategy> activeStrategy = signalEvaluator.resolveActiveStrategy(runner);
        if (activeStrategy.isEmpty()) {
            return;
        }

        StrategyContextDto context = contextAssembler.assemble(runner, currentPrice);
        StrategyOutputDto strategyOutput = signalEvaluator.evaluate(runner, activeStrategy.get(), input, context);
        if (signalEvaluator.isHold(strategyOutput)) {
            signalMetrics.recordSignalEvaluated(runner.getId(), SignalDecision.HOLD);
            log.debug("priceUpdate: HOLD signal for runner={} - no action", runner.getId());
            return;
        }

        // SHOULD_CANCEL e roteado ANTES do pipeline de trade: nao reserva capital, nao normaliza
        // quantidade e nao passa pelas guardas BUY/SELL — apenas puxa uma ordem em transito.
        if (strategyOutput.shouldCancel()) {
            log.debug("priceUpdate: routing SHOULD_CANCEL runner={} target={}",
                    runner.getId(), strategyOutput.targetTransactionId());
            // Re-le o status do runner: o kill-switch/reconciliacao pode ter halted o runner entre
            // canProcessRunner e o retorno da estrategia. Lanca RunnerHaltedException (capturada em
            // execute) — mesma semantica de seguranca dos ramos BUY/SELL antes do dispatch.
            checkRunnerNotHalted(runner.getId());
            cancelSignalHandler.handle(runner, strategyOutput.targetTransactionId(), strategyOutput.reasoning());
            signalMetrics.recordSignalEvaluated(runner.getId(), SignalDecision.CANCEL);
            return;
        }

        log.debug("priceUpdate: routing signal decision={} runner={} symbol={}",
                strategyOutput.decision(), runner.getId(), runner.getSymbol());

        processTradeSignal(runner, strategyOutput, currentPrice, OrderType.LIMIT);
    }

    private void processTradeSignal(StrategyRunner runner, StrategyOutputDto signal, BigDecimal currentPrice,
                                    OrderType orderType) {
        RunnerExposureService.ExposureSnapshot exposureSnapshot = null;
        boolean hasOpenOrInflight = false;
        if (signalPolicy.requiresOpenPositionCheck(runner, signal)) {
            exposureSnapshot = runnerExposureService.loadSnapshot(runner);
            hasOpenOrInflight = exposureSnapshot.hasOpenOrInFlight();
        }
        if (!signalPolicy.canExecuteSignal(runner, signal, hasOpenOrInflight)) {
            // Sinal avaliado mas barrado pela guarda de execução (sobretudo política SINGLE com
            // posição/ordem já aberta): registra REJECTED_POLICY para o tick não parecer
            // "avaliação parada" no monitoramento.
            signalMetrics.recordSignalEvaluated(runner.getId(), SignalDecision.REJECTED_POLICY);
            return;
        }

        // Preço efetivo da ordem: a estratégia pode fixar um limitPrice (ex.: ordem que descansa
        // longe do mercado ou limit marketable). Sem limitPrice, usa o preço de mercado do tick —
        // comportamento default preservado.
        BigDecimal effectivePrice = signal.limitPrice() != null ? signal.limitPrice() : currentPrice;

        // Normaliza quantidade e preço às regras de filtro da exchange ANTES de materializar
        // a transação, de modo que o valor persistido, reservado e enviado sejam idênticos.
        // O preço arredonda a favor do trader conforme o lado (T37).
        OrderNormalizer.NormalizedOrder normalized = orderNormalizer.normalize(
                runner.getExchangeId(), runner.getSymbol(), tradeIntentFactory.orderSideOf(signal),
                signal.quantity(), effectivePrice);

        Transaction transaction = tradeIntentFactory.buildTransaction(
                runner, signal, orderType, normalized.quantity(), normalized.price());

        // T26: o log de CRIACAO so e emitido quando a transacao foi de fato persistida e despachada
        // (outcome DISPATCHED) — senao anunciaria uma transacao fantasma para sinais descartados antes
        // do commit (BUY recusado por capital, SELL sem posicao, falha de lock que faz rollback). O log
        // registra transactionId + correlationId (do tick, via MDC) no texto: o "join" tick->transacao.
        // O MDC estruturado por transactionId fica a cargo da conciliacao, nao deste fluxo de origem.
        try {
            if (transaction.isBuy()) {
                BigDecimal precomputedExposure = exposureSnapshot != null
                        ? exposureSnapshot.inFlightExposure()
                        : null;
                BuyExecutionContext buyContext = tradeIntentFactory
                        .buildBuyExecutionContext(runner, transaction, precomputedExposure);
                BuySignalHandler.BuyOutcome outcome = buySignalHandler.handle(buyContext,
                        this::transactionalPersistBuyAndReserve,
                        this::checkRunnerCanOpen, this::expirePendingBuy);
                if (outcome == BuySignalHandler.BuyOutcome.DISPATCHED) {
                    logTransactionCreated(transaction, runner);
                }
                signalMetrics.recordSignalEvaluated(runner.getId(),
                        outcome == BuySignalHandler.BuyOutcome.DISPATCHED
                                ? SignalDecision.BUY
                                : SignalDecision.REJECTED_CAPITAL);
            } else {
                SellSignalHandler.SellOutcome outcome = sellSignalHandler.handle(runner, signal, transaction,
                        this::transactionalPersistSellAndLockPosition, this::checkRunnerNotHalted,
                        this::expirePendingSell);
                if (outcome == SellSignalHandler.SellOutcome.DISPATCHED) {
                    logTransactionCreated(transaction, runner);
                }
                // NO_OPEN_POSITION tambem e registrado (REJECTED_NO_POSITION): um runner que recebe
                // ticks e tenta vender sem inventario nao pode parecer "avaliacao parada" no monitoramento.
                signalMetrics.recordSignalEvaluated(runner.getId(),
                        outcome == SellSignalHandler.SellOutcome.DISPATCHED
                                ? SignalDecision.SELL
                                : SignalDecision.REJECTED_NO_POSITION);
            }
        } catch (RunnerHaltedException | ConcurrentPositionLockException e) {
            // Halt pos-persist: a transacao ja foi expirada/conciliada (rastreavel pelos logs da
            // conciliacao). Lock concorrente: rollback no persist, a transacao nao foi commitada.
            // Em ambos nao ha transacao "viva e orfa" a registrar aqui.
            throw e;
        } catch (RuntimeException e) {
            // T26: falha de dispatch APOS o persist commitar (ex.: orderDispatch.dispatch lanca) deixa
            // a transacao viva sem nenhum log que cite seu id ate o watchdog. Registra a falha com o
            // transactionId no texto antes de propagar para o catch (por-runner) de execute().
            log.error("signal: dispatch failed after persist transactionId={} runnerId={} side={} - {}",
                    transaction.getId(), runner.getId(), transaction.isBuy() ? "BUY" : "SELL", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Emite o log sentinel de criacao de transacao (T26), que registra o {@code correlationId} do
     * tick (do MDC) ao lado do {@code transactionId} — o "join" tick -> transacao para o Loki.
     * Chamado apenas no caminho de sucesso (transacao persistida e ordem despachada).
     */
    private void logTransactionCreated(Transaction transaction, StrategyRunner runner) {
        log.info("signal: transaction created transactionId={} correlationId={} runnerId={} side={}",
                transaction.getId(), MDC.get("correlationId"), runner.getId(),
                transaction.isBuy() ? "BUY" : "SELL");
    }

}
