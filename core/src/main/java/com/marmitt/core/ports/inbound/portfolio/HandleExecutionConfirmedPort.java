package com.marmitt.core.ports.inbound.portfolio;

import com.marmitt.core.dto.events.ExecutionConfirmedEvent;

/**
 * Port de entrada para processamento da confirmação de execução no Portfolio.
 * <p>
 * Chamado pelo listener Spring após receber {@link ExecutionConfirmedEvent}.
 * Aplica a conversão de margem: Reserved → Realized no {@code GlobalBalance}.
 * <p>
 * Idempotência: verifica {@code matchId} antes de processar — duplicatas são
 * descartadas silenciosamente.
 *
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 5.2.2, 5.5.1</a>
 */
public interface HandleExecutionConfirmedPort {

    /**
     * Processa a confirmação de execução e atualiza o {@code GlobalBalance}.
     *
     * @param event evento contendo o payload de confirmação (matchId, totalCost, fee, isFinal)
     */
    void handle(ExecutionConfirmedEvent event);
}
