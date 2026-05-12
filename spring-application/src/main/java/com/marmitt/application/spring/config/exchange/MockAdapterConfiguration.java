package com.marmitt.application.spring.config.exchange;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.core.ports.outbound.events.EventPublisherPort;
import com.marmitt.core.ports.outbound.websocket.WebSocketPortRegistryPort;
import com.marmitt.mock.adapter.LocalEventWebSocketAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MockAdapterConfiguration {

    @Bean
    public LocalEventWebSocketAdapter mockWebSocketPort(EventPublisherPort eventPublisher,
                                                        WebSocketPortRegistryPort webSocketRegistry) {
        LocalEventWebSocketAdapter ws = new LocalEventWebSocketAdapter(eventPublisher);
        webSocketRegistry.register("MOCK", ws);
        return ws;
    }

    @Bean
    public MockExchangeAdapter mockExchangeAdapter(ObjectMapper objectMapper,
                                                   EventPublisherPort eventPublisher) {
        return new MockExchangeAdapter(objectMapper, eventPublisher);
    }
}
