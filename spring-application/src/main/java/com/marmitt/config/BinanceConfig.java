package com.marmitt.config;

import com.marmitt.binance.BinanceUrlBuilder;
import com.marmitt.binance.listener.BinanceWebSocketListener;
import com.marmitt.core.ports.outbound.ExchangeUrlBuilderPort;
import com.marmitt.core.ports.outbound.WebSocketListenerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BinanceConfig {

    @Bean
    public ExchangeUrlBuilderPort binanceUrlBuilder(){
        return new BinanceUrlBuilder();
    }

    @Bean
    public WebSocketListenerPort binanceWebSocketListener() {
        return new BinanceWebSocketListener();
    }
}
