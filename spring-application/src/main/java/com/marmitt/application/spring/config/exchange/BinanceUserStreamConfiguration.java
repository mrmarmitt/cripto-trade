package com.marmitt.application.spring.config.exchange;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.application.spring.adapter.OkHttp3ListenerConverter;
import com.marmitt.application.spring.adapter.OkHttp3WebSocketAdapter;
import com.marmitt.application.spring.adapter.binance.OkHttpClientAdapter;
import com.marmitt.binance.BinanceConnectionConfig;
import com.marmitt.binance.BinanceUserStreamAdapter;
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
public class BinanceUserStreamConfiguration {

    @Bean
    public OkHttpClient binanceRestClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(30))
                .writeTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Bean
    public BinanceUserStreamAdapter binanceUserStreamAdapter(ObjectMapper objectMapper,
                                                              EventPublisherPort eventPublisher,
                                                              OkHttpClient binanceWebSocketClient,
                                                              OkHttpClient binanceRestClient,
                                                              BinanceProperties properties) {
        var ws = new OkHttp3WebSocketAdapter(binanceWebSocketClient,
                new OkHttp3ListenerConverter(eventPublisher, StreamChannel.USER_DATA));
        var httpClient = new OkHttpClientAdapter(binanceRestClient);
        var config = new BinanceConnectionConfig(
                properties.getApiKey(), properties.getApiSecret(),
                properties.getWsBaseUrl(), properties.getRestBaseUrl());
        return new BinanceUserStreamAdapter(ws, httpClient, objectMapper, config);
    }
}
