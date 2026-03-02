package com.marmitt.core.ports.outbound.exchange.streaming;

import com.marmitt.core.ports.outbound.exchange.adapter.ExchangeUrlBuilderPort;
import com.marmitt.core.ports.outbound.exchange.adapter.ReceivedMessageProcessorPort;
import com.marmitt.core.ports.outbound.exchange.adapter.SenderMessageProcessorPort;
import com.marmitt.core.ports.outbound.websocket.WebSocketPort;

/**
 * Capacidade de streaming de mercado/ordens via WebSocket.
 *
 * <p>Esta porta concentra apenas contratos de conectividade e serializacao
 * ligados ao canal streaming.
 *
 * <p>Etapa 1 da refatoracao:
 * {@code ExchangeAdapterPort} permanece ativo e extende esta interface
 * para manter compatibilidade com os consumidores legados.
 */
public interface ExchangeStreamingPort {

    String getExchangeName();

    boolean requiresPostConnection();

    WebSocketPort getWebSocketPort();

    ReceivedMessageProcessorPort getReceivedMessageProcessor();

    SenderMessageProcessorPort getSenderMessageProcessor();

    ExchangeUrlBuilderPort getUrlBuilder();
}

