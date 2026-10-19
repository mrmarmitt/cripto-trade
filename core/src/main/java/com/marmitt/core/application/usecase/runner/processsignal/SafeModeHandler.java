package com.marmitt.core.application.usecase.runner.processsignal;

import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.strategy.StrategyOutputDto;
import com.marmitt.core.enums.TransactionStatus;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;

import java.util.List;

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
class SafeModeHandler {

    static final String STRATEGY_NAME = "SAFE_MODE";

    private final StrategyRunnerRepositoryPort strategyRunnerRepository;
    private final TradeIntentFactory intentFactory;
    private final CancelSignalHandler cancelSignalHandler;

    SafeModeHandler(StrategyRunnerRepositoryPort strategyRunnerRepository,
                    TradeIntentFactory intentFactory,
                    CancelSignalHandler cancelSignalHandler) {
        this.strategyRunnerRepository = strategyRunnerRepository;
        this.intentFactory = intentFactory;
        this.cancelSignalHandler = cancelSignalHandler;
    }

    /**
     * Envia cancelamento para cada ordem LIMIT em voo do runner. Falha em uma ordem e logada e nao
     * impede as demais; o proximo tick tenta de novo.
     */
    void cancelRestingOrders(StrategyRunner runner) {
        for (Transaction transaction : strategyRunnerRepository.findByRunnerIdAndStatuses(
                runner.getId(), TransactionStatus.IN_FLIGHT)) {
            cancelSignalHandler.cancelResting(runner, transaction, "safe-mode");
        }
    }

    /**
     * Sinais de venda a mercado do lote inteiro para cada posicao aberta e sem lock do runner.
     */
    List<StrategyOutputDto> liquidationSignals(StrategyRunner runner) {
        return strategyRunnerRepository.findOpenPositionsByRunnerId(runner.getId()).stream()
                .filter(position -> !position.isLocked())
                .map(position -> intentFactory.liquidationSignal(STRATEGY_NAME, position,
                        "safe-mode PANIC_SELL liquidation"))
                .toList();
    }
}
