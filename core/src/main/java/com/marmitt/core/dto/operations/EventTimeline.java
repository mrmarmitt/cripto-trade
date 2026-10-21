package com.marmitt.core.dto.operations;

import java.time.Instant;
import java.util.List;

/**
 * Resposta da linha do tempo (T59): eventos em ordem cronologica.
 *
 * @param truncated {@code true} quando havia mais eventos que o {@code limit} no intervalo
 * @param nextSince quando truncada, o {@code since} da proxima pagina (instante do ultimo evento; eventos
 *                  nesse mesmo instante podem voltar e sao descartados pelo {@code id})
 */
public record EventTimeline(
        Instant since,
        Instant until,
        List<TimelineEvent> events,
        boolean truncated,
        Instant nextSince
) {
}
