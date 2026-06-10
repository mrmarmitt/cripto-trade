package com.marmitt.application.spring.config.exchange;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.core.dto.exchange.OrderSubmissionResult;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.dto.websocket.request.SendOrderRequest;
import com.marmitt.core.ports.outbound.events.EventPublisherPort;
import com.marmitt.core.ports.outbound.exchange.ExchangeOrderPort;
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

    @Bean
    public ExchangeOrderPort mockOrderPort(MockExchangeAdapter mockExchangeAdapter,
                                           LocalEventWebSocketAdapter mockWebSocketPort) {
        return new ExchangeOrderPort() {
            @Override
            public String getExchangeName() { return "MOCK"; }

            @Override
            public OrderSubmissionResult submitOrder(SendOrderRequest request) {
                try {
                    OrderDataDto dto = mockExchangeAdapter.submitOrder(request);
                    if (dto == null || dto.status() == null) {
                        mockWebSocketPort.sendMessage(mockExchangeAdapter.formatMessage(request));
                        return OrderSubmissionResult.dispatched();
                    }
                    return OrderSubmissionResult.completed(dto);
                } catch (UnsupportedOperationException ex) {
                    mockWebSocketPort.sendMessage(mockExchangeAdapter.formatMessage(request));
                    return OrderSubmissionResult.dispatched();
                }
            }
        };
    }
}
