package com.marmitt.application.spring.config.exchange;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.application.spring.adapter.OkHttp3ListenerConverter;
import com.marmitt.application.spring.adapter.OkHttp3WebSocketAdapter;
import com.marmitt.binance.BinanceConnectionConfig;
import com.marmitt.binance.BinanceMarketStreamAdapter;
import com.marmitt.core.enums.StreamChannel;
import com.marmitt.core.ports.outbound.events.EventPublisherPort;
import com.marmitt.core.ports.outbound.websocket.WebSocketPortRegistryPort;
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
    public OkHttp3WebSocketAdapter binanceMarketWebSocketPort(OkHttpClient binanceWebSocketClient,
                                                               EventPublisherPort eventPublisher,
                                                               WebSocketPortRegistryPort webSocketRegistry) {
        OkHttp3WebSocketAdapter ws = new OkHttp3WebSocketAdapter(binanceWebSocketClient,
                new OkHttp3ListenerConverter(eventPublisher, StreamChannel.MARKET));
        webSocketRegistry.register("BINANCE", ws);
        return ws;
    }

    @Bean
    public BinanceMarketStreamAdapter binanceMarketStreamAdapter(ObjectMapper objectMapper,
                                                                 BinanceProperties properties) {
        BinanceConnectionConfig config = new BinanceConnectionConfig(
                properties.getApiKey(),
                properties.getApiSecret(),
                properties.getWsBaseUrl(),
                properties.getRestBaseUrl());
        return new BinanceMarketStreamAdapter(objectMapper, config);
    }
}
