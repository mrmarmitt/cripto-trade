package com.marmitt.config;

import com.marmitt.coinbase.CoinbaseUrlBuilder;
import com.marmitt.coinbase.listener.CoinbaseWebSocketListener;
import com.marmitt.core.ports.outbound.ExchangeUrlBuilderPort;
import com.marmitt.core.ports.outbound.websocket.MessageProcessorPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CoinbaseConfig {

    @Bean
    public ExchangeUrlBuilderPort coinbaseUrlBuilder(){
        return new CoinbaseUrlBuilder();
    }

    @Bean
    public MessageProcessorPort coinbaseWebSocketListener() {
        return new CoinbaseWebSocketListener();
    }
}
