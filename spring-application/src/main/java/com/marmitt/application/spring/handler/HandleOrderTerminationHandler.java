package com.marmitt.application.spring.handler;

import com.marmitt.core.application.usecase.runner.HandleOrderTerminationService;
import com.marmitt.core.domain.runner.Position;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Handler Spring do fluxo HandleOrderTermination (F2-03).
 * <p>
 * Gerencia o limite {@code @Transactional} para o encerramento de Transactions
 * que atingiram status terminal ({@code REJECTED}, {@code CANCELED}, {@code EXPIRED}).
 * <p>
 * <b>Fluxo dentro da transação de banco:</b>
 * <ol>
 *   <li>Desbloquear Position (SELL) — {@code unlock()} + {@code reopen()} + {@code savePosition()}</li>
 *   <li>Persistir Transaction com novo status terminal — {@code saveTransaction()}</li>
 *   <li>Publicar {@code MarginReleaseEvent} — processado pelo Portfolio após o commit
 *       via {@code @TransactionalEventListener(AFTER_COMMIT)}</li>
 * </ol>
 * <p>
 * <b>Reusabilidade:</b> este handler é o ponto de entrada único para todos os triggers
 * de HandleOrderTermination:
 * <ul>
 *   <li>Dispatch REJECTED (F2-01): chamado por {@code ProcessTradeSignalHandler.handleAck()}</li>
 *   <li>Exchange callback — CANCELED/EXPIRED (F2-02): chamado pelo callback handler</li>
 *   <li>Watchdog timeout (F2-06/F2-07): chamado pelo Watchdog</li>
 * </ul>
 *
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 6.3, 5.2.3</a>
 */
@Component
@Slf4j
public class HandleOrderTerminationHandler {

    private final HandleOrderTerminationService service;
    private final StrategyRunnerRepositoryPort runnerRepository;

    public HandleOrderTerminationHandler(
            HandleOrderTerminationService service,
            StrategyRunnerRepositoryPort runnerRepository
    ) {
        this.service = service;
        this.runnerRepository = runnerRepository;
    }

    /**
     * Processa o encerramento de uma Transaction em status terminal.
     * <p>
     * <b>Pré-condição:</b> {@code transaction.getStatus()} já deve estar atualizado
     * para o status terminal ({@code reject()}, {@code cancel()} ou {@code expire()}
     * chamados antes deste handler).
     *
     * @param transaction transaction com status terminal já aplicado
     */
    @Transactional
    public void handle(Transaction transaction) {
        log.debug("handleOrderTermination: processing transactionId={} status={}",
                transaction.getId(), transaction.getStatus());

        // Passo 1: desbloquear Position (SELL only)
        Optional<Position> unlockedPosition = service.findAndUnlockPosition(transaction);
        unlockedPosition.ifPresent(runnerRepository::savePosition);

        // Passo 2: persistir Transaction com status terminal
        runnerRepository.saveTransaction(transaction);

        // Passo 3: publicar MarginReleaseEvent (processado AFTER_COMMIT pelo Portfolio)
        service.releaseMargin(transaction);
    }
}
