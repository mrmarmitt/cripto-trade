package com.marmitt.config.exchange;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.adapter.OkHttp3ListenerConverter;
import com.marmitt.adapter.OkHttp3WebSocketAdapter;
import com.marmitt.binance.BinanceUrlBuilder;
import com.marmitt.binance.processor.receive.BinanceReceivedMessageProcessor;
import com.marmitt.binance.processor.send.BinanceSenderMessageProcessor;
import com.marmitt.core.ports.outbound.exchange.adapter.ExchangeUrlBuilderPort;
import com.marmitt.core.ports.outbound.events.EventPublisherPort;
import com.marmitt.core.ports.outbound.exchange.adapter.ReceivedMessageProcessorPort;
import com.marmitt.core.ports.outbound.exchange.adapter.SenderMessageProcessorPort;
import com.marmitt.core.ports.outbound.websocket.WebSocketPort;
import com.marmitt.core.ports.outbound.exchange.adapter.ExchangeAdapterPort;

/**
 * Implementação do ExchangeAdapter para Binance.
 * 
 * Encapsula todos os componentes específicos da Binance:
 * - WebSocketPort usando OkHttp3WebSocketAdapter
 * - BinanceMessageProcessor para processamento de mensagens
 * - BinanceUrlBuilder para construção de URLs
 * 
 * Registra-se automaticamente no ExchangeAdapterRegistry durante a inicialização.
 */
public class BinanceExchangeAdapter implements ExchangeAdapterPort {

    private final WebSocketPort webSocketPort;
    private final ReceivedMessageProcessorPort receivedMessageProcessor;
    private final SenderMessageProcessorPort senderMessageProcessor;
    private final ExchangeUrlBuilderPort urlBuilder;

    public BinanceExchangeAdapter(ObjectMapper objectMapper, EventPublisherPort eventPublisher) {
        this.webSocketPort = new OkHttp3WebSocketAdapter(new OkHttp3ListenerConverter(eventPublisher));
        this.receivedMessageProcessor = new BinanceReceivedMessageProcessor(objectMapper);
        this.senderMessageProcessor = new BinanceSenderMessageProcessor(objectMapper);
        this.urlBuilder = new BinanceUrlBuilder();
    }

    @Override
    public String getExchangeName() {
        return "BINANCE";
    }

    @Override
    public WebSocketPort getWebSocketPort() {
        return webSocketPort;
    }

    @Override
    public ReceivedMessageProcessorPort getReceivedMessageProcessor() {
        return receivedMessageProcessor;
    }

    @Override
    public SenderMessageProcessorPort getSenderMessageProcessor() {
        return this.senderMessageProcessor;
    }

    @Override
    public ExchangeUrlBuilderPort getUrlBuilder() {
        return urlBuilder;
    }
}