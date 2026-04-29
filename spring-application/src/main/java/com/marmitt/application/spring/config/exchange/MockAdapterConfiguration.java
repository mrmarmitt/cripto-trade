package com.marmitt.application.spring.config.exchange;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.core.ports.outbound.events.EventPublisherPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MockAdapterConfiguration {

    @Bean
    public MockExchangeAdapter mockExchangeAdapter(ObjectMapper objectMapper,
                                                   EventPublisherPort eventPublisher) {
        return new MockExchangeAdapter(objectMapper, eventPublisher);
    }
}
