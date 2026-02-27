package com.marmitt.core.application.usecase.runner.orderconciliation;

import com.marmitt.core.domain.runner.ClientOrderId;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.ports.inbound.runner.OrderConciliationPort;
import com.marmitt.core.ports.outbound.events.EventPublisherPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;

/**
 * Orquestrador de conciliacao de ordens: recebe callbacks assincronos da exchange
 * (via WebSocket) e evolui o estado interno das {@link Transaction transacoes} e
 * {@link com.marmitt.core.domain.runner.Position posicoes} do StrategyRunner.
 *
 * <p>A cada evento de ordem recebido, o use case:
 * <ol>
 *   <li>Valida que o {@code clientOrderId} pertence a este sistema (formato v1) —
 *       eventos de outras origens sao descartados silenciosamente.</li>
 *   <li>Localiza a {@link Transaction} local pelo {@code clientOrderId}.</li>
 *   <li>Roteia para o handler adequado conforme o status da exchange:
 *     <ul>
 *       <li>{@code NEW} → confirma aceite da exchange: PENDING → SUBMITTED.</li>
 *       <li>{@code FILLED / PARTIALLY_FILLED} → aplica fill na posicao e/ou cria
 *           {@link com.marmitt.core.domain.runner.TransactionMatch TransactionMatch}.</li>
 *       <li>{@code CANCELED / EXPIRED / REJECTED} → encerra a transacao e devolve
 *           o capital reservado ao {@link com.marmitt.core.domain.portfolio.GlobalBalance}.</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <p><b>Fronteiras transacionais:</b> os metodos {@code transactional*} sao abstratos
 * e implementados pela camada de composicao (Spring) via {@code TransactionTemplate},
 * mantendo o dominio livre de dependencias de infraestrutura.
 *
 * @see FillCalculator
 * @see BuyFillHandler
 * @see SellFillHandler
 * @see TerminationHandler
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Secao 6.2 — OrderConciliation</a>
 */
@Slf4j
public abstract class OrderConciliationUseCase implements OrderConciliationPort {

    private final StrategyRunnerRepositoryPort strategyRunnerRepository;
    private final FillCalculator fillCalculator;
    private final BuyFillHandler buyFillHandler;
    private final SellFillHandler sellFillHandler;
    private final TerminationHandler terminationHandler;

    public OrderConciliationUseCase(StrategyRunnerRepositoryPort strategyRunnerRepository,
                                    EventPublisherPort eventPublisher) {
        this.strategyRunnerRepository = strategyRunnerRepository;
        this.fillCalculator = new FillCalculator();
        this.buyFillHandler = new BuyFillHandler(strategyRunnerRepository);
        this.sellFillHandler = new SellFillHandler(strategyRunnerRepository, eventPublisher);
        this.terminationHandler = new TerminationHandler(
                strategyRunnerRepository, eventPublisher, new MarginReleaseBuilder());
    }

    /**
     * Entry point do use case. Recebe um evento de ordem da exchange e roteia
     * para o handler correspondente conforme o status informado.
     * Eventos cujo {@code clientOrderId} nao pertencam a este sistema sao ignorados.
     */
    public void execute(OrderDataDto orderData) {
        String clientOrderId = orderData.clientOrderId();

        if (!ClientOrderId.isValid(clientOrderId)) {
            log.trace("orderConciliation: clientOrderId={} not in v1 format - skipping", clientOrderId);
            return;
        }

        Transaction transaction = strategyRunnerRepository
                .findTransactionByClientOrderId(clientOrderId)
                .orElse(null);

        if (transaction == null) {
            log.debug("orderConciliation: transaction not found for clientOrderId={} - skipping", clientOrderId);
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
                transactionalSubmit(transaction);
            }
            case FILLED -> transactionalProcessFill(transaction, orderData, true);
            case PARTIALLY_FILLED -> transactionalProcessFill(transaction, orderData, false);
            case CANCELED -> {
                transaction.cancel();
                transactionalReleaseMargin(transaction);
            }
            case EXPIRED -> {
                transaction.expire();
                transactionalReleaseMargin(transaction);
            }
            case REJECTED -> {
                transaction.reject(orderData.rejectReason());
                transactionalReleaseMargin(transaction);
            }
            default -> log.warn("orderConciliation: unexpected status={} for clientOrderId={}",
                    orderData.status(), clientOrderId);
        }
    }

    /**
     * Ponto de extensao para a fronteira transacional do ACK de aceite ({@code NEW}).
     * Implementado pela camada de composicao para envolver {@link #submitTransaction}
     * em uma transacao de banco de dados.
     */
    public abstract void transactionalSubmit(Transaction transaction);

    /**
     * Ponto de extensao para a fronteira transacional de fills ({@code FILLED / PARTIALLY_FILLED}).
     * Implementado pela camada de composicao para envolver {@link #processFill}
     * em uma transacao de banco de dados.
     */
    public abstract void transactionalProcessFill(Transaction transaction, OrderDataDto orderData,
                                                  boolean isFinal);

    /**
     * Ponto de extensao para a fronteira transacional de encerramentos com falha.
     * Implementado pela camada de composicao para envolver {@link #releaseMargin}
     * em uma transacao de banco de dados.
     */
    public abstract void transactionalReleaseMargin(Transaction transaction);

    /**
     * Corpo do ACK de aceite executado dentro da fronteira transacional.
     * Persiste a transacao com status SUBMITTED e o {@code exchangeOrderId}
     * retornado pela exchange — vinculo definitivo entre registro interno e externo.
     */
    protected void submitTransaction(Transaction transaction) {
        strategyRunnerRepository.saveTransaction(transaction);
        log.info("orderConciliation: PENDING->SUBMITTED transactionId={} exchangeOrderId={}",
                transaction.getId(), transaction.getExchangeOrderId());
    }

    /**
     * Corpo do processamento de fill executado dentro da fronteira transacional.
     * Delega o calculo incremental ao {@link FillCalculator} e roteia entre
     * {@link BuyFillHandler} e {@link SellFillHandler} conforme o tipo da transacao.
     */
    protected void processFill(Transaction transaction, OrderDataDto orderData, boolean isFinal) {
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

    /**
     * Corpo do encerramento com falha executado dentro da fronteira transacional.
     * Delega ao {@link TerminationHandler} a sequencia de unlock de posicao,
     * persistencia e publicacao de evento de liberacao de margem.
     */
    public void releaseMargin(Transaction transaction) {
        terminationHandler.handle(transaction);
    }
}
