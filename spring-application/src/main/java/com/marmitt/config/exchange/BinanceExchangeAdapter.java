package com.marmitt.config.exchange;

import com.marmitt.adapter.OkHttp3ListenerConverter;
import com.marmitt.adapter.OkHttp3WebSocketAdapter;
import com.marmitt.binance.BinanceUrlBuilder;
import com.marmitt.binance.processor.BinanceMessageProcessor;
import com.marmitt.core.ports.outbound.ExchangeUrlBuilderPort;
import com.marmitt.core.ports.outbound.events.EventPublisherPort;
import com.marmitt.core.ports.outbound.websocket.AdapterMessageProcessorPort;
import com.marmitt.core.ports.outbound.websocket.WebSocketPort;
import com.marmitt.core.ports.outbound.ExchangeAdapterPort;
import org.springframework.context.ApplicationEventPublisher;

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
    private final AdapterMessageProcessorPort messageProcessor;
    private final ExchangeUrlBuilderPort urlBuilder;

    public BinanceExchangeAdapter(EventPublisherPort eventPublisher) {

        this.webSocketPort = new OkHttp3WebSocketAdapter(new OkHttp3ListenerConverter(eventPublisher));
        this.messageProcessor = new BinanceMessageProcessor();
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
    public AdapterMessageProcessorPort getMessageProcessor() {
        return messageProcessor;
    }

    @Override
    public ExchangeUrlBuilderPort getUrlBuilder() {
        return urlBuilder;
    }
}