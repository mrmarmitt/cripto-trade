package com.marmitt.config.exchange;

import com.marmitt.adapter.OkHttp3ListenerConverter;
import com.marmitt.adapter.OkHttp3WebSocketAdapter;
import com.marmitt.coinbase.CoinbaseUrlBuilder;
import com.marmitt.coinbase.processor.CoinbaseMessageProcessor;
import com.marmitt.core.ports.outbound.exchange.adapter.ExchangeUrlBuilderPort;
import com.marmitt.core.ports.outbound.events.EventPublisherPort;
import com.marmitt.core.ports.outbound.exchange.adapter.ReceivedMessageProcessorPort;
import com.marmitt.core.ports.outbound.exchange.adapter.SenderMessageProcessorPort;
import com.marmitt.core.ports.outbound.websocket.WebSocketPort;
import com.marmitt.core.ports.outbound.exchange.adapter.ExchangeAdapterPort;

/**
 * Implementação do ExchangeAdapter para Coinbase.
 * 
 * Encapsula todos os componentes específicos da Coinbase:
 * - WebSocketPort usando OkHttp3WebSocketAdapter
 * - CoinbaseMessageProcessor para processamento de mensagens
 * - CoinbaseUrlBuilder para construção de URLs
 * 
 * Registra-se automaticamente no ExchangeAdapterRegistry durante a inicialização.
 */
public class CoinbaseExchangeAdapter implements ExchangeAdapterPort {

    private final WebSocketPort webSocketPort;
    private final ReceivedMessageProcessorPort messageProcessor;
    private final ExchangeUrlBuilderPort urlBuilder;

    public CoinbaseExchangeAdapter(EventPublisherPort eventPublisher) {
        this.webSocketPort = new OkHttp3WebSocketAdapter(new OkHttp3ListenerConverter(eventPublisher));
        this.messageProcessor = new CoinbaseMessageProcessor();
        this.urlBuilder = new CoinbaseUrlBuilder();
    }

    @Override
    public String getExchangeName() {
        return "COINBASE";
    }

    @Override
    public WebSocketPort getWebSocketPort() {
        return webSocketPort;
    }

    @Override
    public ReceivedMessageProcessorPort getReceivedMessageProcessor() {
        return messageProcessor;
    }

    @Override
    public SenderMessageProcessorPort getSenderMessageProcessor() {
        return null;
    }

    @Override
    public ExchangeUrlBuilderPort getUrlBuilder() {
        return urlBuilder;
    }
}