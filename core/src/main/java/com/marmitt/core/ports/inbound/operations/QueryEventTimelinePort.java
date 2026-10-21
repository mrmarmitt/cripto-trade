package com.marmitt.core.ports.inbound.operations;

import com.marmitt.core.dto.operations.EventTimeline;

import java.time.Instant;
import java.util.UUID;

/**
 * Linha do tempo de eventos do sistema e do operador (T59): responde "o que aconteceu desde X" sem acesso
 * ao banco nem aos logs.
 */
public interface QueryEventTimelinePort {

    /**
     * @param since       inicio, inclusivo; nulo = 24h antes de {@code until}
     * @param until       fim, exclusivo; nulo = agora
     * @param portfolioId so eventos ligados ao portfolio (Safe Mode, runners, saidas protetivas, DLQ);
     *                    nulo = todos
     * @param limit       maximo de eventos; nulo = padrao
     */
    EventTimeline find(Instant since, Instant until, UUID portfolioId, Integer limit);
}
