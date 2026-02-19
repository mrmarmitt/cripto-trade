package com.marmitt.core.ports.inbound.runner;

import com.marmitt.core.domain.runner.Transaction;

/**
 * Porta de entrada do fluxo HandleOrderTermination.
 * <p>
 * Implementada por {@code HandleOrderTerminationHandler} (spring-application),
 * que gerencia os limites {@code @Transactional}.
 * Chamada pelo {@code RunnerUseCase} ao receber callbacks de ordens terminais
 * (REJECTED, CANCELED, EXPIRED) via {@code OrderUpdateListener}.
 *
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 6.3</a>
 */
public interface HandleOrderTerminationPort {

    /**
     * Processa o encerramento de uma Transaction em status terminal.
     * <p>
     * <b>Pré-condição:</b> o status terminal já deve estar aplicado na Transaction
     * ({@code reject()}, {@code cancel()} ou {@code expire()} chamados pelo caller).
     *
     * @param transaction transaction com status terminal aplicado
     */
    void handle(Transaction transaction);
}
