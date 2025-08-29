package com.marmitt.ctrade.application.service;

import com.marmitt.ctrade.config.TradingProperties;
import com.marmitt.ctrade.controller.dto.HealthCheckResponse;
import com.marmitt.ctrade.domain.port.ExchangeWebSocketAdapter;
import com.marmitt.ctrade.domain.port.WebSocketPort;
//import com.marmitt.ctrade.infrastructure.exchange.mock.MockWebSocketAdapter;
import com.marmitt.ctrade.infrastructure.exchange.binance.BinanceWebSocketAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class HealthCheckService {

    private final PriceCacheService priceCacheService;
    private final BinanceWebSocketAdapter webSocketPort;
    private final TradingProperties tradingProperties;

    public HealthCheckResponse getSystemHealth() {
        HealthCheckResponse response = new HealthCheckResponse();

        response.setTimestamp(LocalDateTime.now());
        response.setCache(getCacheHealth());
        response.setWebSocket(getWebSocketHealth());

        // Status geral baseado nos componentes
        String overallStatus = determineOverallStatus(response.getCache(), response.getWebSocket());
        response.setStatus(overallStatus);

        return response;
    }

    private HealthCheckResponse.CacheHealthInfo getCacheHealth() {
        try {
            int tradingPairs = priceCacheService.getCacheSize();
            int totalEntries = priceCacheService.getTotalHistoryEntries();

            return new HealthCheckResponse.CacheHealthInfo(
                    "UP",
                    tradingPairs,
                    totalEntries,
                    tradingProperties.priceCache().ttlMinutes() + " minutes",
                    tradingProperties.priceCache().maxHistorySize()
            );
        } catch (Exception e) {
            log.error("Error checking cache health", e);
            return new HealthCheckResponse.CacheHealthInfo(
                    "DOWN",
                    0,
                    0,
                    tradingProperties.priceCache().ttlMinutes() + " minutes",
                    tradingProperties.priceCache().maxHistorySize()
            );
        }
    }

    private HealthCheckResponse.WebSocketHealthInfo getWebSocketHealth() {
        try {
            boolean connected = webSocketPort.isConnected();
            int subscribedPairs = 0;
            boolean orderUpdatesSubscribed = false;

            // Se for ExchangeWebSocketAdapter (incluindo Binance), conseguimos mais detalhes
            if (webSocketPort instanceof ExchangeWebSocketAdapter exchangeAdapter) {
                subscribedPairs = exchangeAdapter.getSubscribedPairs().size();
                orderUpdatesSubscribed = exchangeAdapter.isOrderUpdatesSubscribed();
            }

            return new HealthCheckResponse.WebSocketHealthInfo(
                    connected ? "UP" : "DOWN",
                    connected,
                    subscribedPairs,
                    orderUpdatesSubscribed
            );
        } catch (Exception e) {
            log.error("Error checking WebSocket health", e);
            return new HealthCheckResponse.WebSocketHealthInfo(
                    "DOWN",
                    false,
                    0,
                    false
            );
        }
    }

    private String determineOverallStatus(HealthCheckResponse.CacheHealthInfo cache,
                                          HealthCheckResponse.WebSocketHealthInfo webSocket) {
        if ("UP".equals(cache.getStatus()) && "UP".equals(webSocket.getStatus())) {
            return "UP";
        } else if ("DOWN".equals(cache.getStatus()) && "DOWN".equals(webSocket.getStatus())) {
            return "DOWN";
        } else {
            return "DEGRADED";
        }
    }
}