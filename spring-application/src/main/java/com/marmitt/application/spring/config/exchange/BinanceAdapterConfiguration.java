package com.marmitt.application.spring.config.exchange;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.application.spring.adapter.OkHttp3ListenerConverter;
import com.marmitt.application.spring.adapter.OkHttp3WebSocketAdapter;
import com.marmitt.binance.BinanceEndpointConfig;
import com.marmitt.binance.BinanceUrlBuilder;
import com.marmitt.binance.auth.BinanceCredentials;
import com.marmitt.binance.auth.BinanceRequestSigner;
import com.marmitt.binance.processor.receive.BinanceReceivedMessageProcessor;
import com.marmitt.binance.processor.send.BinanceSenderMessageProcessor;
import com.marmitt.core.ports.outbound.events.EventPublisherPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@org.springframework.context.annotation.Configuration
@EnableConfigurationProperties(BinanceProperties.class)
@ConditionalOnExpression("!'${binance.api-key:}'.isBlank() || !'${binance.api-secret:}'.isBlank()")
public class BinanceAdapterConfiguration {

    @Bean
    public BinanceExchangeAdapter binanceExchangeAdapter(ObjectMapper objectMapper,
                                                         EventPublisherPort eventPublisher,
                                                         BinanceProperties properties) {
        var config      = new BinanceEndpointConfig(properties.getWsBaseUrl(), properties.getRestBaseUrl());
        var credentials = new BinanceCredentials(properties.getApiKey(), properties.getApiSecret());
        var signer      = new BinanceRequestSigner(credentials);
        var urlBuilder  = new BinanceUrlBuilder(config);
        var sender      = new BinanceSenderMessageProcessor(objectMapper, signer, urlBuilder);
        var receiver    = new BinanceReceivedMessageProcessor(objectMapper);
        var ws          = new OkHttp3WebSocketAdapter(new OkHttp3ListenerConverter(eventPublisher));
        return new BinanceExchangeAdapter(ws, receiver, sender, urlBuilder);
    }
}
