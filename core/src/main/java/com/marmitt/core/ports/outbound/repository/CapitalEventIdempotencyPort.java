package com.marmitt.core.ports.outbound.repository;

import java.util.UUID;

/**
 * Porta de idempotencia para eventos de capital.
 *
 * <p>Objetivo: garantir at-least-once sem duplicar efeito financeiro
 * quando houver retry/reentrega de eventos.
 */
public interface CapitalEventIdempotencyPort {

    /**
     * Tenta registrar processamento de ExecutionConfirmed para o match informado.
     *
     * @return true se este processamento e o primeiro; false se duplicado
     */
    boolean tryRegisterExecutionConfirmed(UUID matchId);

    /**
     * Tenta registrar processamento de MarginRelease para a transacao informada.
     *
     * @return true se este processamento e o primeiro; false se duplicado
     */
    boolean tryRegisterMarginRelease(UUID transactionId);
}
