package com.marmitt.core.application.usecase.portfolio;

import com.marmitt.core.dto.capital.ExecutionConfirmation;
import com.marmitt.core.ports.inbound.portfolio.ConfirmExecutionPort;

/**
 * Implementação de {@link ConfirmExecutionPort} — Confirmação de execução assíncrona.
 * <b>Não implementado — ver F1-11.</b>
 *
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 5.2.2</a>
 */
public class ConfirmExecutionUseCase implements ConfirmExecutionPort {

    /**
     * @throws UnsupportedOperationException sempre — implementação prevista para F1-11
     */
    @Override
    public void confirmExecution(ExecutionConfirmation confirmation) {
        throw new UnsupportedOperationException(
                "confirmExecution not yet implemented — scheduled for F1-11");
    }
}
