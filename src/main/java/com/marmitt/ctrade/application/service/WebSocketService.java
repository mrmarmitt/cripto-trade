package com.marmitt.ctrade.application.service;

import com.marmitt.ctrade.domain.port.WebSocketPort;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class WebSocketService {
    
    private final WebSocketPort webSocketPort;

    public WebSocketService(WebSocketPort webSocketPort) {
        this.webSocketPort = webSocketPort;
    }
    
//    @PostConstruct
    public void init() {
        startConnection();
        log.info("WebSocketService initialized with event-driven architecture");
    }
    
    public void startConnection() {
        log.info("Starting WebSocket connection");
        webSocketPort.connect();
    }
    
    public void stopConnection() {
        log.info("Stopping WebSocket connection");
        webSocketPort.disconnect();
    }
    
    public void subscribeToTradingPair(String tradingPair) {
        log.info("Subscribing to price updates for: {}", tradingPair);
        webSocketPort.subscribeToPrice(tradingPair);
    }
}