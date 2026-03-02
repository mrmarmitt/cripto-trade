package com.marmitt.core.ports.outbound.exchange.adapter;

import com.marmitt.core.ports.outbound.exchange.streaming.ExchangeStreamingPort;

/**
 * Porta legada de adapter "tudo em um".
 *
 * <p>Etapa 1 da refatoracao:
 * permanece ativa para compatibilidade e agora representa somente
 * a capacidade de streaming (WebSocket).
 *
 * <p>As novas capacidades REST foram separadas em:
 * <ul>
 *   <li>{@code ExchangeOrderExecutionPort}</li>
 *   <li>{@code ExchangeOrderQueryPort}</li>
 *   <li>{@code ExchangeAccountQueryPort}</li>
 * </ul>
 */
public interface ExchangeAdapterPort extends ExchangeStreamingPort {
}
