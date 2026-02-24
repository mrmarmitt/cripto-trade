package com.marmitt.core.application.usecase.runner.processsignal;

import com.marmitt.core.application.exception.CapitalReservationRejectedException;
import com.marmitt.core.dto.capital.BuyExecutionContext;
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
    public void handle(BuyExecutionContext context, BuyPersistenceAction persistenceAction) {
        try {
            persistenceAction.persist(context);
        } catch (CapitalReservationRejectedException ex) {
            log.warn("processBuySignal: signal discarded - capital rejected transactionId={} reason={}",
                    ex.getTransactionId(), ex.getReason());
            return;
        }

        orderDispatch.dispatch(intentFactory.buildDispatchCommand(context.runner(), context.transaction()));
        log.debug("dispatch: order sent - clientOrderId={} stays PENDING until exchange confirms",
                context.transaction().getClientOrderId());
    }

    @FunctionalInterface
    public interface BuyPersistenceAction {
        /**
         * Fronteira transacional para persistencia de BUY e reserva de capital.
         */
        void persist(BuyExecutionContext context);
    }
}
