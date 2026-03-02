package com.marmitt.core.ports.outbound.exchange.rest;

import com.marmitt.core.dto.websocket.data.AccountDataDto;

/**
 * Capacidade REST para consulta de conta/saldo.
 *
 * <p>Principal consumidor: Phase 2 do boot (sanity check de saldo).
 */
public interface ExchangeAccountQueryPort {

    AccountDataDto queryAccountSnapshot();
}

