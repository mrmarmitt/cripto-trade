package com.marmitt.application.spring.handler;

import com.marmitt.core.application.usecase.runner.HandleOrderTerminationUseCase;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.ports.inbound.runner.HandleOrderTerminationPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handler Spring do fluxo HandleOrderTermination (F2-03).
 * <p>
 * Responsabilidade única: garantir o limite {@code @Transactional} para o
 * {@link HandleOrderTerminationUseCase}. Toda a lógica de domínio está no UseCase.
 * <p>
 * <b>Triggers:</b>
 * <ul>
 *   <li>Dispatch REJECTED (F2-01): chamado por {@code ProcessTradeSignalHandler.handleAck()}</li>
 *   <li>Exchange callback — CANCELED/EXPIRED (F2-02): chamado pelo callback handler</li>
 *   <li>Watchdog timeout (F2-06/F2-07): chamado pelo Watchdog</li>
 * </ul>
 *
 * @see HandleOrderTerminationUseCase
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 6.3, 5.2.3</a>
 */
@Component
public class HandleOrderTerminationHandler implements HandleOrderTerminationPort {

    private final HandleOrderTerminationUseCase useCase;

    public HandleOrderTerminationHandler(HandleOrderTerminationUseCase useCase) {
        this.useCase = useCase;
    }

    @Transactional
    public void handle(Transaction transaction) {
        useCase.handle(transaction);
    }
}
