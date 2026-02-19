package com.marmitt.core.application.usecase.runner;

import com.marmitt.core.domain.runner.ClientOrderId;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.strategy.PortfolioContextDto;
import com.marmitt.core.dto.strategy.StrategyInputDto;
import com.marmitt.core.dto.strategy.StrategyOutputDto;
import com.marmitt.core.dto.websocket.data.MarketDataDto;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.ports.inbound.runner.HandleOrderTerminationPort;
import com.marmitt.core.ports.inbound.runner.ProcessTradeSignalPort;
import com.marmitt.core.ports.outbound.listener.OrderUpdateListener;
import com.marmitt.core.ports.outbound.listener.PriceUpdateListener;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import com.marmitt.core.ports.outbound.strategy.TradingStrategy;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * Ponto de entrada único de um StrategyRunner ativo — implementa
 * {@link PriceUpdateListener} e {@link OrderUpdateListener}.
 * <p>
 * Uma instância por Runner ativo. Criada e registrada pelo
 * {@link RunnerLifecycleUseCase} durante a ativação.
 * <p>
 * <b>onPriceUpdate</b>: filtra pelo símbolo e exchange do Runner, verifica se pode
 * aceitar sinais, executa a estratégia e delega o sinal a {@link ProcessTradeSignalPort}.
 * <p>
 * <b>onOrderUpdate</b>: filtra por {@code shortCode} do Runner via
 * {@link ClientOrderId#getRunnerShortCode}, localiza a Transaction pelo
 * {@code clientOrderId} e roteia o callback conforme o {@code OrderStatus}.
 * <p>
 * <b>Simplificações V1:</b>
 * <ul>
 *   <li>{@code previousPrice}: usa o {@code currentPrice} como fallback
 *       (sem rastreamento de preço anterior)</li>
 *   <li>FILLED / PARTIALLY_FILLED: stub de log — será tratado em F2-02</li>
 * </ul>
 *
 * @see RunnerLifecycleUseCase
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 6.4</a>
 */
@Slf4j
public class RunnerUseCase implements PriceUpdateListener, OrderUpdateListener {

    private final StrategyRunner runner;
    private final TradingStrategy strategy;
    private final PortfolioContextBuilder contextBuilder;
    private final ProcessTradeSignalPort processTradeSignalPort;
    private final HandleOrderTerminationPort terminationPort;
    private final StrategyRunnerRepositoryPort runnerRepository;

    public RunnerUseCase(
            StrategyRunner runner,
            TradingStrategy strategy,
            PortfolioContextBuilder contextBuilder,
            ProcessTradeSignalPort processTradeSignalPort,
            HandleOrderTerminationPort terminationPort,
            StrategyRunnerRepositoryPort runnerRepository
    ) {
        this.runner = runner;
        this.strategy = strategy;
        this.contextBuilder = contextBuilder;
        this.processTradeSignalPort = processTradeSignalPort;
        this.terminationPort = terminationPort;
        this.runnerRepository = runnerRepository;
    }

    // ============================================================
    // PriceUpdateListener
    // ============================================================

    /**
     * Recebe atualização de preço e executa a estratégia se os filtros forem satisfeitos.
     * <p>
     * Filtros aplicados em ordem:
     * <ol>
     *   <li>Runner opera neste símbolo e exchange</li>
     *   <li>Runner pode aceitar sinais ({@code ACTIVE} e não em Boot Sequence)</li>
     *   <li>Dados de mercado são válidos ({@link StrategyInputDto#isValid()})</li>
     * </ol>
     */
    @Override
    public void onPriceUpdate(MarketDataDto marketData) {
        if (!runner.operates(marketData.symbol().value(), marketData.exchangeName())) {
            return;
        }

        if (!runner.canAcceptSignals()) {
            log.debug("runnerUseCase: signals not accepted runnerId={} status={} isReconciling={}",
                    runner.getId(), runner.getStatus(), runner.isReconciling());
            return;
        }

        StrategyInputDto input = buildStrategyInput(marketData);
        if (!input.isValid()) {
            log.warn("runnerUseCase: invalid market data discarded runnerId={} symbol={}",
                    runner.getId(), marketData.symbol());
            return;
        }

        PortfolioContextDto context = contextBuilder.build(runner);
        StrategyOutputDto signal = strategy.executeStrategy(input, context);

        processTradeSignalPort.handle(runner, signal, marketData.price());
    }

    // ============================================================
    // OrderUpdateListener
    // ============================================================

    /**
     * Pré-filtro de roteamento: retorna {@code true} somente se o {@code clientOrderId}
     * pertence a este Runner (via {@code shortCode}).
     */
    @Override
    public boolean shouldProcess(OrderDataDto orderData) {
        String shortCode = ClientOrderId.getRunnerShortCode(orderData.clientOrderId());
        return runner.getShortCode().equals(shortCode);
    }

    /**
     * Roteia o callback de ordem para o fluxo correto conforme o {@code OrderStatus}.
     * <ul>
     *   <li>NEW: no-op (Exchange aceitou — ordem já persistida como SUBMITTED)</li>
     *   <li>PARTIALLY_FILLED / FILLED: stub de log — F2-02</li>
     *   <li>CANCELED / REJECTED / EXPIRED: aplica transição de status e delega
     *       ao {@link HandleOrderTerminationPort}</li>
     * </ul>
     */
    @Override
    public void onOrderUpdate(OrderDataDto orderData) {
        if (!shouldProcess(orderData)) {
            return;
        }

        Optional<Transaction> transactionOpt =
                runnerRepository.findTransactionByClientOrderId(orderData.clientOrderId());

        if (transactionOpt.isEmpty()) {
            log.warn("runnerUseCase: transaction not found for clientOrderId={} runnerId={}",
                    orderData.clientOrderId(), runner.getId());
            return;
        }

        routeOrderUpdate(transactionOpt.get(), orderData);
    }

    // ============================================================
    // Private helpers
    // ============================================================

    private StrategyInputDto buildStrategyInput(MarketDataDto marketData) {
        return StrategyInputDto.builder()
                .symbol(marketData.symbol())
                .currentPrice(marketData.price())
                .previousPrice(marketData.price()) // V1: sem rastreamento de preço anterior
                .bidPrice(marketData.bidPrice())
                .askPrice(marketData.askPrice())
                .volume(marketData.volume())
                .high24h(marketData.high24h())
                .low24h(marketData.low24h())
                .timestamp(marketData.timestamp())
                .additionalData(null)
                .build();
    }

    private void routeOrderUpdate(Transaction transaction, OrderDataDto orderData) {
        switch (orderData.status()) {
            case NEW -> log.debug("runnerUseCase: order NEW (no-op) transactionId={}", transaction.getId());
            case PARTIALLY_FILLED -> log.info(
                    "runnerUseCase: PARTIALLY_FILLED — F2-02 stub transactionId={}", transaction.getId());
            case FILLED -> log.info(
                    "runnerUseCase: FILLED — F2-02 stub transactionId={}", transaction.getId());
            case CANCELED -> {
                transaction.cancel();
                terminationPort.handle(transaction);
            }
            case REJECTED -> {
                String reason = orderData.rejectReason() != null ? orderData.rejectReason() : "exchange rejected";
                transaction.reject(reason);
                terminationPort.handle(transaction);
            }
            case EXPIRED -> {
                transaction.expire();
                terminationPort.handle(transaction);
            }
        }
    }
}
