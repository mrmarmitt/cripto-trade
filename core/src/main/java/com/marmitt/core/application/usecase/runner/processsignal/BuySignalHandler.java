package com.marmitt.core.application.usecase.runner.processsignal;

import com.marmitt.core.application.exception.CapitalReservationRejectedException;
import com.marmitt.core.dto.capital.BuyExecutionContext;
import com.marmitt.core.ports.outbound.exchange.OrderDispatchPort;
import lombok.extern.slf4j.Slf4j;

/**
 * Executa o ramo BUY do pipeline de sinais: persistencia transacional seguida de dispatch.
 *
 * <p><b>Protocolo persist-first:</b> a transacao e salva no banco com status PENDING
 * <em>antes</em> do dispatch para a exchange. Isso garante que, em caso de falha apos
 * o envio mas antes do ACK, o sistema pode detectar e reconciliar a ordem pendente.
 *
 * <p><b>Rejeicao silenciosa por capital:</b> se a {@link com.marmitt.core.application.exception.CapitalReservationRejectedException}
 * for lancada durante a persistencia, o sinal e descartado e logado como aviso — nao e
 * um erro de sistema, e uma decisao de risco esperada (saldo insuficiente, Safe Mode,
 * limite de exposicao atingido).
 *
 * <p><b>Fronteira transacional:</b> a persistencia ocorre dentro de uma transacao de banco
 * injetada via {@link BuyPersistenceAction}. O handler em si nao conhece Spring — a
 * fronteira e definida pela camada de composicao.
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
     * Persiste a transacao BUY dentro da fronteira transacional e despacha para a exchange.
     * Se a reserva de capital for rejeitada, o metodo retorna sem dispatch — o sinal e
     * descartado sem propagacao de excecao para o chamador.
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

    /**
     * Seam de persistencia transacional do ramo BUY.
     * Implementado pela camada de composicao (Spring) para executar
     * {@link ProcessTradeSignalUseCase#persistBuyAndReserve} dentro de uma transacao.
     */
    @FunctionalInterface
    public interface BuyPersistenceAction {
        void persist(BuyExecutionContext context);
    }
}
