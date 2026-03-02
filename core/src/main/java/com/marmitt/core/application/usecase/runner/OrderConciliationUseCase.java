package com.marmitt.core.application.usecase.runner;

import com.marmitt.core.application.usecase.runner.orderconciliation.ReconcileOrderUpdate;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.ports.inbound.runner.OrderConciliationPort;

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
public abstract class OrderConciliationUseCase implements OrderConciliationPort {

    private final ReconcileOrderUpdate reconcileOrderUpdate;

    public OrderConciliationUseCase(ReconcileOrderUpdate reconcileOrderUpdate) {
        this.reconcileOrderUpdate = reconcileOrderUpdate;
    }

    /**
     * Entry point do use case. Recebe um evento de ordem da exchange e roteia
     * para o handler correspondente conforme o status informado.
     * Eventos cujo {@code clientOrderId} nao pertencam a este sistema sao ignorados.
     */
    public void execute(OrderDataDto orderData) {
        reconcileOrderUpdate.execute(
                orderData,
                this::transactionalSubmit,
                this::transactionalProcessFill,
                this::transactionalReleaseMargin
        );
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
        reconcileOrderUpdate.submitTransaction(transaction);
    }

    /**
     * Corpo do processamento de fill executado dentro da fronteira transacional.
     * Delega o calculo incremental ao {@link FillCalculator} e roteia entre
     * {@link BuyFillHandler} e {@link SellFillHandler} conforme o tipo da transacao.
     */
    protected void processFill(Transaction transaction, OrderDataDto orderData, boolean isFinal) {
        reconcileOrderUpdate.processFill(transaction, orderData, isFinal);
    }

    /**
     * Corpo do encerramento com falha executado dentro da fronteira transacional.
     * Delega ao {@link TerminationHandler} a sequencia de unlock de posicao,
     * persistencia e publicacao de evento de liberacao de margem.
     */
    public void releaseMargin(Transaction transaction) {
        reconcileOrderUpdate.releaseMargin(transaction);
    }
}
