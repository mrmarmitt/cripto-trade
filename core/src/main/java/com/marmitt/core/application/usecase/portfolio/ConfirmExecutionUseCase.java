package com.marmitt.core.application.usecase.portfolio;

import com.marmitt.core.dto.capital.ExecutionConfirmation;
import com.marmitt.core.ports.inbound.portfolio.ConfirmExecutionPort;

/**
 * Implementação de {@link ConfirmExecutionPort} — Confirmação de execução assíncrona.
 *
 * @implNote Implementação pendente para F1-11. Quando implementado, converterá margem de
 *           Reserved → Realized no {@code GlobalBalance} via {@code ApplicationEventPublisher}
 *           com {@code @TransactionalEventListener(phase = AFTER_COMMIT)} e retry exponencial.
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 5.2.2</a>
 */
public class ConfirmExecutionUseCase implements ConfirmExecutionPort {

    /**
     * @implNote Não implementado — ver F1-11.
     * @throws UnsupportedOperationException sempre
     */
    @Override
    public void confirmExecution(ExecutionConfirmation confirmation) {
        throw new UnsupportedOperationException(
                "confirmExecution not yet implemented — scheduled for F1-11");
    }
}
