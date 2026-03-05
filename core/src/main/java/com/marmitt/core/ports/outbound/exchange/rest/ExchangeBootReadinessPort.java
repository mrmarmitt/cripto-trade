package com.marmitt.core.ports.outbound.exchange.rest;

import com.marmitt.core.dto.exchange.boot.ExchangeBootReadiness;

/**
 * Capacidade de readiness para boot (Phase 1: infraestrutura).
 *
 * <p>Permite fail-fast quando a exchange nao esta pronta para operacao.
 */
public interface ExchangeBootReadinessPort {

    ExchangeBootReadiness checkBootReadiness();
}
