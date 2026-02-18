package com.marmitt.core.ports.inbound.portfolio;

import com.marmitt.core.dto.events.MarginReleaseEvent;

/**
 * Port de entrada para processamento do estorno de margem no Portfolio.
 * <p>
 * Chamado pelo listener Spring após receber {@link MarginReleaseEvent}.
 * Aplica a devolução de margem: Reserved → Available no {@code GlobalBalance}.
 * <p>
 * Criticidade: este evento <b>nunca pode ser perdido</b> — capital preso é inaceitável.
 * O listener associado utiliza retries ilimitados.
 *
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 5.2.3, 5.5.1</a>
 */
public interface HandleMarginReleasePort {

    /**
     * Processa o estorno de margem e atualiza o {@code GlobalBalance}.
     *
     * @param event evento contendo o payload de release (transactionId, releaseAmount, reason)
     */
    void handle(MarginReleaseEvent event);
}
