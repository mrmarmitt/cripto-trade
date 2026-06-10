package com.marmitt.application.spring.config.exchange;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.application.spring.adapter.OkHttp3ListenerConverter;
import com.marmitt.application.spring.adapter.OkHttp3WebSocketAdapter;
import com.marmitt.application.spring.adapter.binance.OkHttpClientAdapter;
import com.marmitt.binance.BinanceConnectionConfig;
import com.marmitt.binance.BinanceMarketStreamAdapter;
import com.marmitt.binance.BinanceOrderAdapter;
import com.marmitt.binance.filters.SymbolFilterCache;
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
    public OkHttpClient binanceRestClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(30))
                .writeTimeout(Duration.ofSeconds(10))
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
    public SymbolFilterCache binanceSymbolFilterCache(ObjectMapper objectMapper,
                                                      BinanceProperties properties,
                                                      OkHttpClient binanceRestClient) {
        SymbolFilterCache cache = new SymbolFilterCache(
                properties.getRestBaseUrl(),
                new OkHttpClientAdapter(binanceRestClient),
                objectMapper);
        // Eager-load configured symbols at startup; SymbolFilterLoadException propagates and aborts boot
        properties.getSymbols().forEach(cache::loadAndCache);
        return cache;
    }

    @Bean
    public BinanceMarketStreamAdapter binanceMarketStreamAdapter(ObjectMapper objectMapper,
                                                                 BinanceProperties properties,
                                                                 OkHttpClient binanceRestClient,
                                                                 SymbolFilterCache binanceSymbolFilterCache) {
        BinanceConnectionConfig config = new BinanceConnectionConfig(
                properties.getApiKey(),
                properties.getApiSecret(),
                properties.getWsBaseUrl(),
                properties.getRestBaseUrl());
        // Derived client shares binanceRestClient's connection pool; callTimeout caps the full
        // boot readiness check to 10s instead of the shared client's 30s read timeout.
        OkHttpClient bootReadinessClient = binanceRestClient.newBuilder()
                .callTimeout(Duration.ofSeconds(10))
                .build();
        return new BinanceMarketStreamAdapter(objectMapper, config,
                new OkHttpClientAdapter(bootReadinessClient), binanceSymbolFilterCache);
    }

    @Bean
    public BinanceOrderAdapter binanceOrderAdapter(OkHttp3WebSocketAdapter binanceUserStreamWebSocketPort,
                                                   OkHttp3WebSocketAdapter binanceMarketWebSocketPort,
                                                   BinanceMarketStreamAdapter binanceMarketStreamAdapter) {
        return new BinanceOrderAdapter(
                binanceUserStreamWebSocketPort,
                binanceMarketWebSocketPort,
                binanceMarketStreamAdapter,
                binanceMarketStreamAdapter
        );
    }
}
