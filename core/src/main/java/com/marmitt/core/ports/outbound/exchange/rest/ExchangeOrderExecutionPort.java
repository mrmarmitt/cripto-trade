package com.marmitt.core.ports.outbound.exchange.rest;

import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.dto.websocket.request.SendCancelOrderRequest;
import com.marmitt.core.dto.websocket.request.SendOrderRequest;

/**
 * Capacidade REST para comando de ordens (write path).
 *
 * <p>Uso esperado:
 * <ul>
 *   <li>submit de ordens no caminho principal, quando a exchange operar via HTTP</li>
 *   <li>cancelamento por watchdog/recovery</li>
 * </ul>
 *
 * <p>Observacao:
 * a implementacao pode retornar snapshot parcial da ordem.
 * Eventos posteriores continuam chegando por streaming.
 */
public interface ExchangeOrderExecutionPort {

    OrderDataDto submitOrder(SendOrderRequest request);

    OrderDataDto cancelOrder(SendCancelOrderRequest request);
}

