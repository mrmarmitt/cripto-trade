package com.marmitt.core.application.usecase.runner.processsignal;

import com.marmitt.core.domain.runner.Position;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.strategy.StrategyOutputDto;
import com.marmitt.core.ports.outbound.exchange.OrderDispatchPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

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

    public void handle(StrategyRunner runner,
                       StrategyOutputDto signal,
                       Transaction transaction,
                       SellPersistenceAction persistenceAction) {
        Optional<Position> targetPosition = findTargetPosition(runner, signal);
        if (targetPosition.isEmpty()) {
            log.warn("processSellSignal: SELL signal discarded - no open position for runner={} symbol={}",
                    runner.getId(), runner.getSymbol());
            return;
        }

        persistenceAction.persist(transaction, targetPosition.get());

        orderDispatch.dispatch(intentFactory.buildDispatchCommand(runner, transaction));
        log.debug("dispatch: order sent - clientOrderId={} stays PENDING until exchange confirms",
                transaction.getClientOrderId());
    }

    private Optional<Position> findTargetPosition(StrategyRunner runner, StrategyOutputDto signal) {
        if (signal.targetLotId() != null) {
            return strategyRunnerRepository.findPositionById(signal.targetLotId());
        }
        return strategyRunnerRepository.findOpenPositionByRunnerIdAndSymbol(runner.getId(), runner.getSymbol());
    }

    @FunctionalInterface
    public interface SellPersistenceAction {
        void persist(Transaction transaction, Position targetPosition);
    }
}
