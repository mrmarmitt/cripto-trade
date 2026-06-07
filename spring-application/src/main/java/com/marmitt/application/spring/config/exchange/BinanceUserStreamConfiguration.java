package com.marmitt.application.spring.config.exchange;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.application.spring.adapter.OkHttp3ListenerConverter;
import com.marmitt.application.spring.adapter.OkHttp3WebSocketAdapter;
import com.marmitt.binance.BinanceUserStreamAdapter;
import com.marmitt.binance.BinanceUserStreamSessionAdapter;
import com.marmitt.binance.auth.BinanceCredentials;
import com.marmitt.core.enums.StreamChannel;
import com.marmitt.core.ports.outbound.events.EventPublisherPort;
import com.marmitt.core.ports.outbound.websocket.WebSocketPortRegistryPort;
import okhttp3.OkHttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(BinanceProperties.class)
@ConditionalOnExpression("!'${binance.api-key:}'.isBlank() && !'${binance.api-secret:}'.isBlank()")
public class BinanceUserStreamConfiguration {

    @Bean
    public OkHttp3WebSocketAdapter binanceUserStreamWebSocketPort(OkHttpClient binanceWebSocketClient,
                                                                   EventPublisherPort eventPublisher,
                                                                   WebSocketPortRegistryPort webSocketRegistry) {
        OkHttp3WebSocketAdapter ws = new OkHttp3WebSocketAdapter(binanceWebSocketClient,
                new OkHttp3ListenerConverter(eventPublisher, StreamChannel.USER_DATA));
        webSocketRegistry.registerUserStream("BINANCE", ws);
        return ws;
    }

    @Bean
    public BinanceUserStreamSessionAdapter binanceUserStreamSessionAdapter(ObjectMapper objectMapper,
                                                                            BinanceProperties properties) {
        var credentials = new BinanceCredentials(properties.getApiKey(), properties.getApiSecret());
        return new BinanceUserStreamSessionAdapter(properties.getWsApiBaseUrl(), credentials, objectMapper);
    }

    @Bean
    public BinanceUserStreamAdapter binanceUserStreamAdapter(ObjectMapper objectMapper) {
        return new BinanceUserStreamAdapter(objectMapper);
    }
}
