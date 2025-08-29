package com.marmitt.ctrade.application.exchange.factory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.ctrade.config.TradingProperties;
import com.marmitt.ctrade.infrastructure.config.WebSocketProperties;
import com.marmitt.ctrade.infrastructure.exchange.binance.BinanceTradingPairProvider;
import com.marmitt.ctrade.infrastructure.exchange.binance.BinanceWebSocketAdapter;
import com.marmitt.ctrade.infrastructure.websocket.ConnectionManager;
import com.marmitt.ctrade.infrastructure.websocket.ConnectionStatsTracker;
import com.marmitt.ctrade.infrastructure.websocket.WebSocketConnectionHandler;
import com.marmitt.ctrade.infrastructure.websocket.WebSocketEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BinanceWebSocketFactory {

    private final WebSocketProperties webSocketProperties;
    private final WebSocketConnectionHandler webSocketConnectionHandler;
    private final ConnectionManager connectionManager;
    private final ConnectionStatsTracker connectionStatsTracker;
    private final WebSocketEventPublisher webSocketEventPublisher;
    private final BinanceTradingPairProvider binanceTradingPairProvider;
    private final ObjectMapper objectMapper;

    public BinanceWebSocketFactory(
            WebSocketProperties webSocketProperties,
            WebSocketConnectionHandler webSocketConnectionHandler,
            ConnectionManager connectionManager,
            ConnectionStatsTracker connectionStatsTracker,
            WebSocketEventPublisher webSocketEventPublisher,
            TradingProperties tradingProperties,
            ObjectMapper objectMapper) {

        this.webSocketProperties = webSocketProperties;
        this.webSocketConnectionHandler = webSocketConnectionHandler;
        this.connectionManager = connectionManager;
        this.connectionStatsTracker = connectionStatsTracker;
        this.webSocketEventPublisher = webSocketEventPublisher;
        this.binanceTradingPairProvider = new BinanceTradingPairProvider(tradingProperties);
        this.objectMapper = objectMapper;
    }

    @Bean
    public BinanceWebSocketAdapter binanceWebSocketAdapter(){
        return new BinanceWebSocketAdapter(
                webSocketProperties,
                webSocketConnectionHandler,
                connectionManager,
                connectionStatsTracker,
                webSocketEventPublisher,
                binanceTradingPairProvider,
                objectMapper);
    }
}
