package com.marmitt.core.application.usecase.runner.orderconciliation;

import com.marmitt.core.domain.runner.ClientOrderId;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.ports.outbound.events.EventPublisherPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;

/**
 * Nucleo de reconciliacao de atualizacao de ordem.
 *
 * <p>Centraliza o roteamento por status e a aplicacao das transicoes de estado
 * em transacao/posicao/margem. Pode ser usado com fronteira transacional externa
 * (callbacks) ou com persistencia direta (boot recovery).
 */
@Slf4j
public class ConciliationOrderUpdate {

    @FunctionalInterface
    public interface SubmitAction {
        void apply(Transaction transaction);
    }

    @FunctionalInterface
    public interface FillAction {
        void apply(Transaction transaction, OrderDataDto orderData, boolean isFinal);
    }

    @FunctionalInterface
    public interface ReleaseAction {
        void apply(Transaction transaction);
    }

    private final StrategyRunnerRepositoryPort strategyRunnerRepository;
    private final FillCalculator fillCalculator;
    private final BuyFillHandler buyFillHandler;
    private final SellFillHandler sellFillHandler;
    private final TerminationHandler terminationHandler;

    public ConciliationOrderUpdate(StrategyRunnerRepositoryPort strategyRunnerRepository,
                                   EventPublisherPort eventPublisher) {
        this.strategyRunnerRepository = strategyRunnerRepository;
        this.fillCalculator = new FillCalculator();
        this.buyFillHandler = new BuyFillHandler(strategyRunnerRepository);
        this.sellFillHandler = new SellFillHandler(strategyRunnerRepository, eventPublisher);
        this.terminationHandler = new TerminationHandler(
                strategyRunnerRepository, eventPublisher, new MarginReleaseBuilder());
    }

    /**
     * Roteia um evento de ordem para as acoes de submit/fill/release fornecidas.
     * Ideal para uso com fronteiras transacionais externas.
     */
    public void execute(OrderDataDto orderData,
                        SubmitAction submitAction,
                        FillAction fillAction,
                        ReleaseAction releaseAction) {
        String clientOrderId = orderData.clientOrderId();

        if (!ClientOrderId.isValid(clientOrderId)) {
            log.warn("orderConciliation: clientOrderId={} not in v1 format - skipping", clientOrderId);
            return;
        }

        Transaction transaction = strategyRunnerRepository
                .findTransactionByClientOrderId(clientOrderId)
                .orElse(null);

        if (transaction == null) {
            log.warn("orderConciliation: transaction not found for clientOrderId={} - skipping", clientOrderId);
            return;
        }

        log.debug("orderConciliation: routing clientOrderId={} transactionId={} status={}",
                clientOrderId, transaction.getId(), orderData.status());

        switch (orderData.status()) {
            case NEW -> {
                if (!transaction.isPending()) {
                    log.debug("orderConciliation: duplicate NEW ignored transactionId={} status={}",
                            transaction.getId(), transaction.getStatus());
                    return;
                }
                transaction.submit(orderData.orderId());
                submitAction.apply(transaction);
            }
            case FILLED -> fillAction.apply(transaction, orderData, true);
            case PARTIALLY_FILLED -> fillAction.apply(transaction, orderData, false);
            case CANCELED -> {
                transaction.cancel();
                releaseAction.apply(transaction);
            }
            case EXPIRED -> {
                transaction.expire();
                releaseAction.apply(transaction);
            }
            case REJECTED -> {
                transaction.reject(orderData.rejectReason());
                releaseAction.apply(transaction);
            }
            default -> log.warn("orderConciliation: unexpected status={} for clientOrderId={}",
                    orderData.status(), clientOrderId);
        }
    }

    /**
     * Atalho para reconciliacao com persistencia direta.
     * Usado no boot recovery para evitar acoplamento com outro use case.
     */
    public void execute(OrderDataDto orderData) {
        execute(orderData, this::submitTransaction, this::processFill, this::releaseMargin);
    }

    public void submitTransaction(Transaction transaction) {
        strategyRunnerRepository.saveTransaction(transaction);
        log.info("orderConciliation: PENDING->SUBMITTED transactionId={} exchangeOrderId={}",
                transaction.getId(), transaction.getExchangeOrderId());
    }

    public void processFill(Transaction transaction, OrderDataDto orderData, boolean isFinal) {
        if (transaction.isPending() && orderData.orderId() != null) {
            transaction.submit(orderData.orderId());
            log.debug("orderConciliation: PENDING->SUBMITTED via fill transactionId={} exchangeOrderId={}",
                    transaction.getId(), orderData.orderId());
        }
        if (isFinal) {
            BigDecimal incoming = orderData.executedQuantity();
            BigDecimal current = transaction.getEffectiveExecutedQuantity();
            if (incoming != null && incoming.compareTo(current) <= 0) {
                log.debug("orderConciliation: duplicate FILLED ignored transactionId={} currentQty={} incomingQty={}",
                        transaction.getId(), current, incoming);
                return;
            }
            if (transaction.isFinal()) {
                log.debug("orderConciliation: duplicate FILLED ignored transactionId={} status={}",
                        transaction.getId(), transaction.getStatus());
                return;
            }
        } else {
            BigDecimal incoming = orderData.executedQuantity();
            BigDecimal current = transaction.getEffectiveExecutedQuantity();
            if (incoming == null || incoming.compareTo(current) <= 0) {
                log.debug("orderConciliation: duplicate PARTIALLY_FILLED ignored transactionId={} currentQty={} incomingQty={}",
                        transaction.getId(), current, incoming);
                return;
            }
        }
        FillCalculator.FillComputation computed = fillCalculator.compute(transaction, orderData, isFinal);
        if (transaction.isBuy()) {
            buyFillHandler.handle(transaction, computed.fillIncrement(), computed.fillPrice());
        } else {
            sellFillHandler.handle(transaction, orderData,
                    computed.fillIncrement(), computed.fillPrice(), isFinal);
        }
    }

    public void releaseMargin(Transaction transaction) {
        terminationHandler.handle(transaction);
    }
}
