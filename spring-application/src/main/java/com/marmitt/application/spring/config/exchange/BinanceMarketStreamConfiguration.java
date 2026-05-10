package com.marmitt.application.spring.config.exchange;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.application.spring.adapter.OkHttp3ListenerConverter;
import com.marmitt.application.spring.adapter.OkHttp3WebSocketAdapter;
import com.marmitt.binance.BinanceApiConfig;
import com.marmitt.binance.BinanceMarketStreamAdapter;
import com.marmitt.binance.BinanceUrlBuilder;
import com.marmitt.binance.auth.BinanceCredentials;
import com.marmitt.binance.auth.BinanceRequestSigner;
import com.marmitt.binance.processor.receive.BinanceReceivedMessageProcessor;
import com.marmitt.binance.processor.send.BinanceSenderMessageProcessor;
import com.marmitt.core.enums.StreamChannel;
import com.marmitt.core.ports.outbound.events.EventPublisherPort;
import okhttp3.OkHttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(BinanceProperties.class)
@ConditionalOnExpression("!'${binance.api-key:}'.isBlank() && !'${binance.api-secret:}'.isBlank()")
public class BinanceMarketStreamConfiguration {

    @Bean
    public OkHttpClient binanceWebSocketClient() {
        return new OkHttpClient.Builder()
                .readTimeout(Duration.ZERO)
                .pingInterval(Duration.ofSeconds(20))
                .build();
    }

    @Bean
    public BinanceMarketStreamAdapter binanceMarketStreamAdapter(ObjectMapper objectMapper,
                                                                  EventPublisherPort eventPublisher,
                                                                  OkHttpClient binanceWebSocketClient,
                                                                  BinanceProperties properties) {
        var config      = new BinanceApiConfig(properties.getWsBaseUrl(), properties.getRestBaseUrl());
        var credentials = new BinanceCredentials(properties.getApiKey(), properties.getApiSecret());
        var signer      = new BinanceRequestSigner(credentials);
        var urlBuilder  = new BinanceUrlBuilder(config);
        var sender      = new BinanceSenderMessageProcessor(objectMapper, signer, urlBuilder);
        var receiver    = new BinanceReceivedMessageProcessor(objectMapper);
        var ws          = new OkHttp3WebSocketAdapter(binanceWebSocketClient,
                new OkHttp3ListenerConverter(eventPublisher, StreamChannel.MARKET));
        return new BinanceMarketStreamAdapter(ws, receiver, sender, urlBuilder);
    }
}
