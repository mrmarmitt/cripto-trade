package com.marmitt.application.spring.config.exchange;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.application.spring.adapter.OkHttp3ListenerConverter;
import com.marmitt.application.spring.adapter.OkHttp3WebSocketAdapter;
import com.marmitt.core.dto.exchange.OrderSubmissionResult;
import com.marmitt.core.dto.websocket.request.SendOrderRequest;
import com.marmitt.core.enums.StreamChannel;
import com.marmitt.core.ports.outbound.events.EventPublisherPort;
import com.marmitt.core.ports.outbound.exchange.ExchangeOrderPort;
import com.marmitt.core.ports.outbound.websocket.WebSocketPortRegistryPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CoinbaseAdapterConfiguration {

    @Bean
    public OkHttp3WebSocketAdapter coinbaseWebSocketPort(EventPublisherPort eventPublisher,
                                                         WebSocketPortRegistryPort webSocketRegistry) {
        OkHttp3WebSocketAdapter ws = new OkHttp3WebSocketAdapter(
                new OkHttp3ListenerConverter(eventPublisher, StreamChannel.MARKET));
        webSocketRegistry.register("COINBASE", ws);
        return ws;
    }

    @Bean
    public CoinbaseExchangeAdapter coinbaseExchangeAdapter(ObjectMapper objectMapper) {
        return new CoinbaseExchangeAdapter(objectMapper);
    }

    // REST not implemented for Coinbase — orders are dispatched via streaming only
    @Bean
    public ExchangeOrderPort coinbaseOrderPort(CoinbaseExchangeAdapter coinbaseExchangeAdapter,
                                               OkHttp3WebSocketAdapter coinbaseWebSocketPort) {
        return new ExchangeOrderPort() {
            @Override
            public String getExchangeName() { return "COINBASE"; }

            @Override
            public OrderSubmissionResult submitOrder(SendOrderRequest request) {
                coinbaseWebSocketPort.sendMessage(coinbaseExchangeAdapter.formatMessage(request));
                return OrderSubmissionResult.dispatched();
            }
        };
    }
}
