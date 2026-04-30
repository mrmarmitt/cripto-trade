package com.marmitt.application.spring.config.exchange;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.binance.auth.BinanceCredentials;
import com.marmitt.core.ports.outbound.events.EventPublisherPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(BinanceProperties.class)
@ConditionalOnExpression("!'${binance.api-key:}'.isBlank() || !'${binance.api-secret:}'.isBlank()")
public class BinanceAdapterConfiguration {

    @Bean
    public BinanceExchangeAdapter binanceExchangeAdapter(ObjectMapper objectMapper,
                                                         EventPublisherPort eventPublisher,
                                                         BinanceProperties properties) {
        com.marmitt.binance.Configuration configuration = new com.marmitt.binance.Configuration(
                properties.getWsBaseUrl(),
                properties.getRestBaseUrl()
        );
        BinanceCredentials credentials = new BinanceCredentials(properties.getApiKey(), properties.getApiSecret());
        return new BinanceExchangeAdapter(objectMapper, eventPublisher, configuration, credentials);
    }
}
