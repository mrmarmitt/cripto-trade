package com.marmitt.core.application.usecase.runner.processsignal;

import com.marmitt.core.domain.runner.Position;
import com.marmitt.core.domain.runner.PositionProtection;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.dto.strategy.StrategyOutputDto;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * Protecao do lote independente da estrategia (T44). Avaliada pelo {@link ProcessTradeSignalUseCase}
 * a cada tick, antes da estrategia e em qualquer nivel do Safe Mode: so reduz exposicao.
 *
 * <p>O runner tem no maximo um lote ativo (OPEN ou CLOSING) por simbolo — novas compras somam ao lote.
 * Quando o lote viola um limite de {@link PositionProtection}:
 * <ul>
 *   <li>sem lock: vira um sinal de venda MARKET do lote inteiro, pelo pipeline normal de SELL;</li>
 *   <li>travado por uma SELL LIMIT em voo (da estrategia): o cancelamento e enviado; a venda
 *       protetiva sai num tick seguinte, quando o CANCELED destravar o lote;</li>
 *   <li>travado por uma SELL MARKET (a propria saida ou o PANIC_SELL): nada a fazer.</li>
 * </ul>
 * Sem estado proprio: cada tick reavalia o lote, o que torna a protecao convergente apos restart.
 */
class PositionProtectionHandler {

    static final String STRATEGY_NAME = "POSITION_PROTECTION";

    private final StrategyRunnerRepositoryPort strategyRunnerRepository;
    private final TradeIntentFactory intentFactory;
    private final CancelSignalHandler cancelSignalHandler;

    PositionProtectionHandler(StrategyRunnerRepositoryPort strategyRunnerRepository,
                              TradeIntentFactory intentFactory,
                              CancelSignalHandler cancelSignalHandler) {
        this.strategyRunnerRepository = strategyRunnerRepository;
        this.intentFactory = intentFactory;
        this.cancelSignalHandler = cancelSignalHandler;
    }

    /**
     * Lote ativo do runner que viola um limite ao preco do tick, se houver.
     */
    Optional<Trigger> findTrigger(StrategyRunner runner, BigDecimal price, Instant now) {
        return strategyRunnerRepository.findActivePositionByRunnerIdAndSymbol(runner.getId(), runner.getSymbol())
                .flatMap(lot -> runner.getPositionProtection().violation(lot, price, now)
                        .map(violation -> new Trigger(lot, violation)));
    }

    /** Sinal de venda MARKET do lote inteiro que violou o limite. */
    StrategyOutputDto exitSignal(Trigger trigger) {
        return intentFactory.liquidationSignal(STRATEGY_NAME, trigger.lot(),
                PositionProtection.EXIT_REASONING_PREFIX + trigger.violation().reason() + ": " + trigger.violation().detail());
    }

    /**
     * Cancela a SELL LIMIT que trava o lote, para a saida protetiva poder vende-lo.
     *
     * @return {@code true} quando o cancelamento foi enviado; {@code false} quando o lote esta travado
     *         por uma venda que nao se cancela (MARKET) ou que ja saiu de voo
     */
    boolean releaseLot(StrategyRunner runner, Trigger trigger) {
        return strategyRunnerRepository.findTransactionById(trigger.lot().getLockedByTransactionId())
                .map(lockingSell -> cancelSignalHandler.cancelResting(runner, lockingSell,
                        PositionProtection.EXIT_REASONING_PREFIX + trigger.violation().reason()))
                .orElse(false);
    }

    record Trigger(Position lot, PositionProtection.Violation violation) {
    }
}
