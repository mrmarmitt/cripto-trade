package com.marmitt.core.application.usecase.runner.processsignal;

import com.marmitt.core.domain.runner.Position;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.strategy.StrategyOutputDto;
import com.marmitt.core.exceptions.RunnerHaltedException;
import com.marmitt.core.ports.outbound.exchange.OrderDispatchPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.UUID;

/**
 * Executa o ramo SELL do pipeline de sinais: resolucao do lote alvo, lock transacional
 * da posicao e dispatch para a exchange.
 *
 * <p><b>Resolucao do lote alvo:</b> se o sinal informar um {@code targetLotId} explicito,
 * aquele lote especifico e usado. Caso contrario, o handler busca a posicao aberta mais
 * recente do runner para o simbolo (comportamento FIFO implicito). Se nenhuma posicao
 * aberta existir, o sinal e descartado — nao ha o que vender.
 *
 * <p><b>Lock da posicao:</b> a posicao e bloqueada atomicamente junto com a criacao da
 * transacao SELL dentro da fronteira transacional. O lock impede que dois sinais SELL
 * simultaneos tentem fechar a mesma posicao.
 *
 * <p><b>Fronteira transacional:</b> o lock e a persistencia da transacao ocorrem dentro
 * de uma transacao de banco injetada via {@link SellPersistenceAction}. O handler nao
 * conhece Spring diretamente.
 */
@Slf4j
class SellSignalHandler {

    private final StrategyRunnerRepositoryPort strategyRunnerRepository;
    private final TradeIntentFactory intentFactory;
    private final OrderDispatchPort orderDispatch;

    public SellSignalHandler(StrategyRunnerRepositoryPort strategyRunnerRepository,
                             TradeIntentFactory intentFactory,
                             OrderDispatchPort orderDispatch) {
        this.strategyRunnerRepository = strategyRunnerRepository;
        this.intentFactory = intentFactory;
        this.orderDispatch = orderDispatch;
    }

    /**
     * Resolve o lote alvo, persiste o lock transacionalmente e despacha para a exchange.
     * Se nenhuma posicao aberta existir, o sinal e descartado sem propagacao de excecao.
     *
     * @return {@link SellOutcome#DISPATCHED} quando a ordem foi enviada;
     *         {@link SellOutcome#NO_OPEN_POSITION} quando nao havia posicao aberta para vender.
     */
    public SellOutcome handle(StrategyRunner runner,
                              StrategyOutputDto signal,
                              Transaction transaction,
                              SellPersistenceAction persistenceAction,
                              BuySignalHandler.PreDispatchGuard preDispatchGuard,
                              OnHaltAction onHaltAction) {
        Optional<Position> targetPosition = findTargetPosition(runner, signal);
        if (targetPosition.isEmpty()) {
            log.warn("processSellSignal: SELL signal discarded - no open position for runner={} symbol={}",
                    runner.getId(), runner.getSymbol());
            return SellOutcome.NO_OPEN_POSITION;
        }

        persistenceAction.persist(transaction, targetPosition.get());

        try {
            preDispatchGuard.check(runner.getId());
        } catch (RunnerHaltedException ex) {
            // Persist already committed — expire the SELL transaction so the position
            // lock is released immediately via TerminationHandler.findAndUnlockPosition().
            log.warn("processSellSignal: runner halted after persist - expiring transaction clientOrderId={}",
                    transaction.getClientOrderId());
            onHaltAction.expire(transaction);
            throw ex;
        }

        orderDispatch.dispatch(intentFactory.buildDispatchCommand(runner, transaction));
        log.debug("dispatch: order sent - clientOrderId={} stays PENDING until exchange confirms",
                transaction.getClientOrderId());
        return SellOutcome.DISPATCHED;
    }

    /**
     * Desfecho observavel do ramo SELL. Conflito de lock concorrente continua propagando
     * {@link com.marmitt.core.exceptions.ConcurrentPositionLockException}, e halt pos-persist
     * continua propagando {@link RunnerHaltedException} para o chamador.
     */
    public enum SellOutcome {
        DISPATCHED,
        NO_OPEN_POSITION
    }

    private Optional<Position> findTargetPosition(StrategyRunner runner, StrategyOutputDto signal) {
        if (signal.targetLotId() != null) {
            return strategyRunnerRepository.findPositionById(signal.targetLotId());
        }
        return strategyRunnerRepository.findOpenPositionByRunnerIdAndSymbol(runner.getId(), runner.getSymbol());
    }

    /**
     * Seam de persistencia transacional do ramo SELL.
     * Implementado pela camada de composicao (Spring) para executar
     * {@link ProcessTradeSignalUseCase#persistSellAndLockPosition} dentro de uma transacao.
     */
    @FunctionalInterface
    public interface SellPersistenceAction {
        void persist(Transaction transaction, Position targetPosition);
    }

    @FunctionalInterface
    public interface OnHaltAction {
        void expire(Transaction transaction);
    }
}
