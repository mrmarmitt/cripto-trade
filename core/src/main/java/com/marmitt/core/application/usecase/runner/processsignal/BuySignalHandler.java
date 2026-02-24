package com.marmitt.core.application.usecase.runner.processsignal;

import com.marmitt.core.application.exception.CapitalReservationRejectedException;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.capital.CapitalRequest;
import com.marmitt.core.ports.outbound.exchange.OrderDispatchPort;
import lombok.extern.slf4j.Slf4j;

/**
 * Handler do ramo BUY do fluxo de sinal.
 * Orquestra: capital request -> persistencia transacional -> dispatch.
 */
@Slf4j
class BuySignalHandler {

    private final TradeIntentFactory intentFactory;
    private final OrderDispatchPort orderDispatch;

    public BuySignalHandler(TradeIntentFactory intentFactory, OrderDispatchPort orderDispatch) {
        this.intentFactory = intentFactory;
        this.orderDispatch = orderDispatch;
    }

    /**
     * Processa intencao de compra para o runner.
     */
    public void handle(StrategyRunner runner,
                       Transaction transaction,
                       BuyPersistenceAction persistenceAction) {
        CapitalRequest capitalRequest = intentFactory.buildCapitalRequest(runner, transaction);

        try {
            persistenceAction.persist(transaction, capitalRequest, runner);
        } catch (CapitalReservationRejectedException ex) {
            log.warn("processBuySignal: signal discarded - capital rejected transactionId={} reason={}",
                    ex.getTransactionId(), ex.getReason());
            return;
        }

        orderDispatch.dispatch(intentFactory.buildDispatchCommand(runner, transaction));
        log.debug("dispatch: order sent - clientOrderId={} stays PENDING until exchange confirms",
                transaction.getClientOrderId());
    }

    @FunctionalInterface
    public interface BuyPersistenceAction {
        /**
         * Fronteira transacional para persistencia de BUY e reserva de capital.
         */
        void persist(Transaction transaction, CapitalRequest capitalRequest, StrategyRunner runner);
    }
}
