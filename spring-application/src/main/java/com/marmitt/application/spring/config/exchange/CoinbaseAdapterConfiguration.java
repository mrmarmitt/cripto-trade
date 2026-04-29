package com.marmitt.application.spring.config.exchange;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.core.ports.outbound.events.EventPublisherPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CoinbaseAdapterConfiguration {

    @Bean
    public CoinbaseExchangeAdapter coinbaseExchangeAdapter(ObjectMapper objectMapper,
                                                           EventPublisherPort eventPublisher) {
        return new CoinbaseExchangeAdapter(objectMapper, eventPublisher);
    }
}
