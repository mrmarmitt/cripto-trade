package com.marmitt.ctrade.application.exchange;

import com.marmitt.ctrade.domain.port.ExchangePort;
import com.marmitt.ctrade.domain.port.WebSocketPort;
import com.marmitt.ctrade.infrastructure.exchange.ExchangeConnectionAdapter;
import com.marmitt.ctrade.infrastructure.exchange.ExchangePortAdapter;
import com.marmitt.ctrade.infrastructure.exchange.binance.BinanceWebSocketAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class AdapterConfig {

    @Bean
    public Map<ExchangeConnectionAdapter, WebSocketPort> connectionAdapters(
            BinanceWebSocketAdapter binanceWebSocketAdapter
    ) {
        Map<ExchangeConnectionAdapter, WebSocketPort> webSocketAdapters = new HashMap<>();

        webSocketAdapters.put(ExchangeConnectionAdapter.MOCK_RANDOM, null);
        webSocketAdapters.put(ExchangeConnectionAdapter.MOCK_STRICT, null);
        webSocketAdapters.put(ExchangeConnectionAdapter.BINANCE, binanceWebSocketAdapter);

        return webSocketAdapters;
    }


    @Bean
    public Map<ExchangePortAdapter, ExchangePort> exchangePortAdapters() {
        Map<ExchangePortAdapter, ExchangePort> exchangePortAdapters = new HashMap<>();

        exchangePortAdapters.put(ExchangePortAdapter.BINANCE, null);

        return exchangePortAdapters;
    }

}
