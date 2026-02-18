package com.marmitt.core.application.usecase.portfolio;

import com.marmitt.core.dto.capital.ExecutionConfirmation;
import com.marmitt.core.dto.events.ExecutionConfirmedEvent;
import com.marmitt.core.ports.inbound.portfolio.ConfirmExecutionPort;
import com.marmitt.core.ports.outbound.events.EventPublisherPort;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementação de {@link ConfirmExecutionPort} — lado Runner do fluxo de confirmação (F1-11).
 * <p>
 * Responsabilidade: publicar {@link ExecutionConfirmedEvent} de forma fire-and-forget.
 * O processamento contábil (Reserved → Realized no GlobalBalance) ocorre no Portfolio
 * via {@code HandleExecutionConfirmedPort}, disparado pelo listener Spring após o commit.
 * <p>
 * Garantia de entrega: at-least-once via {@code @TransactionalEventListener(AFTER_COMMIT)}
 * + {@code @Retryable} no listener (max 5 tentativas, backoff exponencial).
 *
 * @implNote Publicação síncrona no mesmo thread do Runner. O processamento pelo Portfolio
 *           é assíncrono e ocorre após o commit da transação do Runner.
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 5.2.2, 5.3.1</a>
 */
@Slf4j
public class ConfirmExecutionUseCase implements ConfirmExecutionPort {

    private final EventPublisherPort eventPublisher;

    public ConfirmExecutionUseCase(EventPublisherPort eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * Publica o evento de confirmação de execução.
     * Retorna imediatamente — o processamento pelo Portfolio é assíncrono.
     *
     * @param confirmation payload com matchId, totalCost, fee e isFinal
     */
    @Override
    public void confirmExecution(ExecutionConfirmation confirmation) {
        log.debug("confirmExecution: publishing event transactionId={} matchId={} isFinal={}",
                confirmation.transactionId(), confirmation.matchId(), confirmation.isFinal());
        eventPublisher.publishEvent(new ExecutionConfirmedEvent(confirmation));
    }
}
