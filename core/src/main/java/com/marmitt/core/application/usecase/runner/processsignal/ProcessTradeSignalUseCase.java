package com.marmitt.core.application.usecase.runner.processsignal;

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
import com.marmitt.core.ports.inbound.runner.OrderConciliationPort;
import com.marmitt.core.ports.inbound.runner.ProcessTradeSignalPort;
import com.marmitt.core.ports.outbound.strategy.TradingStrategy;
import com.marmitt.core.ports.outbound.exchange.OrderDispatchPort;
import com.marmitt.core.ports.outbound.exchange.rest.OrderQuantityNormalizerPort;
import com.marmitt.core.ports.outbound.metrics.SignalDecision;
import com.marmitt.core.ports.outbound.metrics.SignalMetricsPort;
import com.marmitt.core.ports.outbound.repository.GlobalBalanceRepositoryPort;
import com.marmitt.core.ports.outbound.repository.PortfolioRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import com.marmitt.core.exceptions.ConcurrentPositionLockException;
import com.marmitt.core.exceptions.RunnerHaltedException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
public abstract class ProcessTradeSignalUseCase implements ProcessTradeSignalPort {

    private static final BigDecimal MINIMUM_OPERATION_AMOUNT = BigDecimal.valueOf(10);

    private final StrategyRunnerRepositoryPort strategyRunnerRepository;
    private final OrderConciliationPort orderConciliation;
    private final SignalMetricsPort signalMetrics;

    private final RunnerSignalPolicy signalPolicy;
    private final StrategySignalEvaluator signalEvaluator;
    private final RunnerContextAssembler contextAssembler;
    private final TradeIntentFactory tradeIntentFactory;
    private final OrderNormalizer orderNormalizer;
    private final BuySignalHandler buySignalHandler;
    private final SellSignalHandler sellSignalHandler;
    private final CancelSignalHandler cancelSignalHandler;
    private final RunnerExposureService runnerExposureService;
    private final CapitalReservationPolicy capitalReservationPolicy;

    protected ProcessTradeSignalUseCase(StrategyRunnerRepositoryPort strategyRunnerRepository,
                                        StrategyRepositoryPort strategyRepository,
                                        GlobalBalanceRepositoryPort globalBalanceRepository,
                                        PortfolioRepositoryPort portfolioRepository,
                                        OrderDispatchPort orderDispatch,
                                        OrderConciliationPort orderConciliation,
                                        List<OrderQuantityNormalizerPort> orderNormalizers,
                                        SignalMetricsPort signalMetrics) {
        this.strategyRunnerRepository = strategyRunnerRepository;
        this.orderConciliation = orderConciliation;
        this.signalMetrics = signalMetrics;

        this.signalPolicy = new RunnerSignalPolicy();
        this.signalEvaluator = new StrategySignalEvaluator(strategyRepository);
        this.contextAssembler = new RunnerContextAssembler(
                globalBalanceRepository, strategyRunnerRepository, MINIMUM_OPERATION_AMOUNT);
        this.tradeIntentFactory = new TradeIntentFactory();
        this.orderNormalizer = new OrderNormalizer(orderNormalizers);
        this.buySignalHandler = new BuySignalHandler(this.tradeIntentFactory, orderDispatch);
        this.sellSignalHandler = new SellSignalHandler(strategyRunnerRepository, this.tradeIntentFactory, orderDispatch);
        this.cancelSignalHandler = new CancelSignalHandler(strategyRunnerRepository, orderDispatch);
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
        // Re-read runner inside the transaction to close the race with the kill switch.
        // PostgreSQL READ COMMITTED ensures this sees any HALTED status committed since the signal was loaded.
        StrategyRunner current = strategyRunnerRepository.findById(context.runner().getId())
                .orElseThrow(() -> new IllegalStateException("Runner disappeared mid-signal: " + context.runner().getId()));
        if (!current.canAcceptSignals()) {
            throw new RunnerHaltedException(context.runner().getId());
        }

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
                Instant.now()
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
                Instant.now()
        );
        orderConciliation.execute(rejected);
    }

    private void checkRunnerNotHalted(UUID runnerId) {
        StrategyRunner current = strategyRunnerRepository.findById(runnerId)
                .orElseThrow(() -> new IllegalStateException("Runner disappeared mid-signal: " + runnerId));
        if (!current.canAcceptSignals()) {
            throw new RunnerHaltedException(runnerId);
        }
    }

    protected void persistSellAndLockPosition(Transaction transaction, Position targetPosition) {
        StrategyRunner current = strategyRunnerRepository.findById(transaction.getRunnerId())
                .orElseThrow(() -> new IllegalStateException("Runner disappeared mid-signal: " + transaction.getRunnerId()));
        if (!current.canAcceptSignals()) {
            throw new RunnerHaltedException(transaction.getRunnerId());
        }

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

        StrategyInputDto strategyInput = tradeIntentFactory.buildStrategyInput(marketData);
        for (StrategyRunner runner : strategyRunners) {
            if (!signalPolicy.canProcessRunner(runner, exchangeId)) {
                continue;
            }

            try {
                processRunner(runner, strategyInput, marketData.price());
            } catch (RunnerHaltedException e) {
                log.warn("priceUpdate: signal discarded - runner halted mid-flight runnerId={} symbol={}",
                        runner.getId(), symbol);
            } catch (ConcurrentPositionLockException e) {
                signalMetrics.recordSignalEvaluated(runner.getId(), SignalDecision.REJECTED_LOCK);
                log.warn("priceUpdate: concurrent SELL conflict for runner={} symbol={} - tick discarded (position already locked by another thread)",
                        runner.getId(), symbol);
            } catch (Exception e) {
                log.error("priceUpdate: error processing runner={} symbol={} - {}",
                        runner.getId(), symbol, e.getMessage(), e);
            } finally {
                // T26: limpa o transactionId por-runner. Mantê-lo setado ate aqui garante que os
                // catches acima (ex.: falha de dispatch apos o commit da transacao) carreguem o
                // transactionId no log de erro — eles rodam fora do escopo de processTradeSignal.
                MDC.remove("transactionId");
            }
        }
    }

    private void processRunner(StrategyRunner runner, StrategyInputDto input, BigDecimal currentPrice) {
        Optional<TradingStrategy> activeStrategy = signalEvaluator.resolveActiveStrategy(runner);
        if (activeStrategy.isEmpty()) {
            return;
        }

        StrategyContextDto context = contextAssembler.assemble(runner);
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
            cancelSignalHandler.handle(runner, strategyOutput);
            signalMetrics.recordSignalEvaluated(runner.getId(), SignalDecision.CANCEL);
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
            // Sinal avaliado mas barrado pela guarda de execução (sobretudo política SINGLE com
            // posição/ordem já aberta): registra REJECTED_POLICY para o tick não parecer
            // "avaliação parada" no monitoramento.
            signalMetrics.recordSignalEvaluated(runner.getId(), SignalDecision.REJECTED_POLICY);
            return;
        }

        // Normaliza quantidade e preço às regras de filtro da exchange ANTES de materializar
        // a transação, de modo que o valor persistido, reservado e enviado sejam idênticos.
        OrderNormalizer.NormalizedOrder normalized = orderNormalizer.normalize(
                runner.getExchangeId(), runner.getSymbol(), signal.quantity(), currentPrice);

        Transaction transaction = tradeIntentFactory.buildTransaction(
                runner, signal, normalized.quantity(), normalized.price());

        // T26: ancora transactionId no MDC ao materializar a transacao, para que os logs desta
        // operacao sejam recuperaveis no Loki via `| json | transactionId="X"`. A limpeza (remove)
        // ocorre no finally por-runner de execute() — assim, um erro tardio (ex.: falha de dispatch
        // apos o commit) capturado la fora ainda carrega o transactionId. O log de CRIACAO so e
        // emitido quando a transacao foi de fato persistida e despachada (outcome DISPATCHED) — senao
        // anunciaria uma transacao fantasma para sinais descartados antes do commit (BUY recusado por
        // capital, SELL sem posicao, falha de lock que faz rollback do TransactionTemplate).
        MDC.put("transactionId", transaction.getId().toString());
        if (transaction.isBuy()) {
            BigDecimal precomputedExposure = exposureSnapshot != null
                    ? exposureSnapshot.inFlightExposure()
                    : null;
            BuyExecutionContext buyContext = tradeIntentFactory
                    .buildBuyExecutionContext(runner, transaction, precomputedExposure);
            BuySignalHandler.BuyOutcome outcome = buySignalHandler.handle(buyContext,
                    this::transactionalPersistBuyAndReserve,
                    this::checkRunnerNotHalted, this::expirePendingBuy);
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
