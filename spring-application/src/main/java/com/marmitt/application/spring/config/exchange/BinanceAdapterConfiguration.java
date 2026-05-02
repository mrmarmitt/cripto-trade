package com.marmitt.application.spring.config.exchange;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.application.spring.adapter.OkHttp3ListenerConverter;
import com.marmitt.application.spring.adapter.OkHttp3WebSocketAdapter;
import com.marmitt.binance.BinanceEndpointConfig;
import com.marmitt.binance.BinanceUrlBuilder;
import com.marmitt.binance.auth.BinanceCredentials;
import com.marmitt.binance.auth.BinanceRequestSigner;
import com.marmitt.binance.processor.receive.BinanceReceivedMessageProcessor;
import com.marmitt.binance.processor.receive.BinanceUserDataProcessor;
import com.marmitt.binance.processor.send.BinanceSenderMessageProcessor;
import com.marmitt.application.spring.adapter.binance.OkHttpListenKeyManager;
import com.marmitt.core.enums.StreamChannel;
import com.marmitt.core.ports.outbound.events.EventPublisherPort;
import okhttp3.OkHttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.time.Duration;

@org.springframework.context.annotation.Configuration
@EnableConfigurationProperties(BinanceProperties.class)
@ConditionalOnExpression("!'${binance.api-key:}'.isBlank() || !'${binance.api-secret:}'.isBlank()")
public class BinanceAdapterConfiguration {

    @Bean
    public OkHttpClient binanceHttpClient() {
        return new OkHttpClient.Builder()
                .readTimeout(Duration.ZERO)
                .pingInterval(Duration.ofSeconds(20))
                .build();
    }

    @Bean
    public BinanceMarketStreamAdapter binanceMarketStreamAdapter(ObjectMapper objectMapper,
                                                                  EventPublisherPort eventPublisher,
                                                                  OkHttpClient binanceHttpClient,
                                                                  BinanceProperties properties) {
        var config      = new BinanceEndpointConfig(properties.getWsBaseUrl(), properties.getRestBaseUrl());
        var credentials = new BinanceCredentials(properties.getApiKey(), properties.getApiSecret());
        var signer      = new BinanceRequestSigner(credentials);
        var urlBuilder  = new BinanceUrlBuilder(config);
        var sender      = new BinanceSenderMessageProcessor(objectMapper, signer, urlBuilder);
        var receiver    = new BinanceReceivedMessageProcessor(objectMapper);
        var ws          = new OkHttp3WebSocketAdapter(binanceHttpClient,
                new OkHttp3ListenerConverter(eventPublisher, StreamChannel.MARKET));
        return new BinanceMarketStreamAdapter(ws, receiver, sender, urlBuilder);
    }

    @Bean
    public BinanceUserStreamAdapter binanceUserStreamAdapter(ObjectMapper objectMapper,
                                                              EventPublisherPort eventPublisher,
                                                              OkHttpClient binanceHttpClient,
                                                              BinanceProperties properties) {
        var config       = new BinanceEndpointConfig(properties.getWsBaseUrl(), properties.getRestBaseUrl());
        var credentials  = new BinanceCredentials(properties.getApiKey(), properties.getApiSecret());
        var listenKeyMgr = new OkHttpListenKeyManager(config.getRestBaseUrl(), credentials, binanceHttpClient, objectMapper);
        var receiver     = new BinanceUserDataProcessor(objectMapper);
        var ws           = new OkHttp3WebSocketAdapter(binanceHttpClient,
                new OkHttp3ListenerConverter(eventPublisher, StreamChannel.USER_DATA));
        return new BinanceUserStreamAdapter(ws, receiver, listenKeyMgr, config.getWebSocketBaseUrl());
    }
}
