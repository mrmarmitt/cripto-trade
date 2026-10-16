package com.marmitt.core.application.usecase.runner.processsignal;

import com.marmitt.core.domain.runner.Position;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.strategy.StrategyOutputDto;
import com.marmitt.core.enums.OrderType;
import com.marmitt.core.enums.TradingAction;
import com.marmitt.core.enums.TransactionStatus;
import com.marmitt.core.ports.outbound.exchange.OrderDispatchPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Acoes do Safe Mode sobre um runner nos niveis {@code CANCEL_ALL} e {@code PANIC_SELL} (T45).
 *
 * <p>Executado pelo {@link ProcessTradeSignalUseCase} no lugar da estrategia, a cada tick do runner.
 * Cada tick reaplica o nivel sobre o estado atual, o que torna a execucao convergente e idempotente:
 * <ul>
 *   <li>ordens LIMIT em voo recebem cancelamento (reenvio deduplicado pelo dispatch);</li>
 *   <li>em {@code PANIC_SELL}, cada posicao aberta sem lock vira um sinal de venda MARKET do lote
 *       inteiro, que segue o pipeline normal de SELL (normalizacao, lock, persistencia, dispatch).
 *       Posicao travada por uma SELL LIMIT so e vendida depois que o cancelamento a destrava;
 *       posicao travada pela propria liquidacao nao e vendida de novo.</li>
 * </ul>
 * Ordens MARKET nao sao canceladas: executam de imediato e sao a propria liquidacao.
 * Persistencia do nivel garante a retomada apos restart sem estado de progresso proprio.
 */
@Slf4j
class SafeModeHandler {

    static final String STRATEGY_NAME = "SAFE_MODE";

    private final StrategyRunnerRepositoryPort strategyRunnerRepository;
    private final TradeIntentFactory intentFactory;
    private final OrderDispatchPort orderDispatch;

    SafeModeHandler(StrategyRunnerRepositoryPort strategyRunnerRepository,
                    TradeIntentFactory intentFactory,
                    OrderDispatchPort orderDispatch) {
        this.strategyRunnerRepository = strategyRunnerRepository;
        this.intentFactory = intentFactory;
        this.orderDispatch = orderDispatch;
    }

    /**
     * Envia cancelamento para cada ordem LIMIT em voo do runner. Falha em uma ordem e logada e nao
     * impede as demais; o proximo tick tenta de novo.
     */
    void cancelRestingOrders(StrategyRunner runner) {
        for (Transaction transaction : strategyRunnerRepository.findByRunnerIdAndStatuses(
                runner.getId(), TransactionStatus.IN_FLIGHT)) {
            if (transaction.getOrderType() != OrderType.LIMIT) {
                continue;
            }
            try {
                orderDispatch.cancel(intentFactory.buildCancelCommand(runner, transaction));
                log.info("safeMode: cancel dispatched runnerId={} transactionId={} clientOrderId={}",
                        runner.getId(), transaction.getId(), transaction.getClientOrderId());
            } catch (RuntimeException e) {
                log.warn("safeMode: cancel failed runnerId={} transactionId={} - retried next tick: {}",
                        runner.getId(), transaction.getId(), e.getMessage());
            }
        }
    }

    /**
     * Sinais de venda a mercado do lote inteiro para cada posicao aberta e sem lock do runner.
     */
    List<StrategyOutputDto> liquidationSignals(StrategyRunner runner) {
        return strategyRunnerRepository.findOpenPositionsByRunnerId(runner.getId()).stream()
                .filter(position -> !position.isLocked())
                .map(position -> liquidationSignal(position, "safe-mode PANIC_SELL liquidation"))
                .toList();
    }

    OrderType liquidationOrderType() {
        return OrderType.MARKET;
    }

    /**
     * Sinal de venda a mercado do lote inteiro. Fonte unica da liquidacao: Safe Mode e comando admin
     * de fechar posicao (T49).
     */
    StrategyOutputDto liquidationSignal(Position position, String reasoning) {
        return new StrategyOutputDto(STRATEGY_NAME, TradingAction.SHOULD_SELL, BigDecimal.ONE,
                position.getQuantity(), null, position.getId(), null, reasoning, Instant.now(), Map.of());
    }
}
