package com.marmitt.application.spring.config.exchange;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.application.spring.adapter.OkHttp3ListenerConverter;
import com.marmitt.application.spring.adapter.OkHttp3WebSocketAdapter;
import com.marmitt.coinbase.CoinbaseUrlBuilder;
import com.marmitt.coinbase.processor.receive.CoinbaseReceivedMessageProcessor;
import com.marmitt.coinbase.processor.send.CoinbaseSenderMessageProcessor;
import com.marmitt.core.ports.outbound.events.EventPublisherPort;
import com.marmitt.core.ports.outbound.exchange.adapter.ExchangeAdapterPort;
import com.marmitt.core.ports.outbound.exchange.adapter.ExchangeUrlBuilderPort;
import com.marmitt.core.ports.outbound.exchange.adapter.ReceivedMessageProcessorPort;
import com.marmitt.core.ports.outbound.exchange.adapter.SenderMessageProcessorPort;
import com.marmitt.core.ports.outbound.websocket.WebSocketPort;

public class CoinbaseExchangeAdapter implements ExchangeAdapterPort {

    private final WebSocketPort webSocketPort;
    private final ReceivedMessageProcessorPort receivedMessageProcessor;
    private final SenderMessageProcessorPort senderMessageProcessor;
    private final ExchangeUrlBuilderPort urlBuilder;

    public CoinbaseExchangeAdapter(ObjectMapper objectMapper, EventPublisherPort eventPublisher) {
        this.webSocketPort = new OkHttp3WebSocketAdapter(new OkHttp3ListenerConverter(eventPublisher));
        this.receivedMessageProcessor = new CoinbaseReceivedMessageProcessor(objectMapper);
        this.senderMessageProcessor = new CoinbaseSenderMessageProcessor(objectMapper);
        this.urlBuilder = new CoinbaseUrlBuilder();
    }

    @Override
    public String getExchangeName() {
        return "COINBASE";
    }

    @Override
    public boolean requiresPostConnection() {
        return true;
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
        return senderMessageProcessor;
    }

    @Override
    public ExchangeUrlBuilderPort getUrlBuilder() {
        return urlBuilder;
    }
}