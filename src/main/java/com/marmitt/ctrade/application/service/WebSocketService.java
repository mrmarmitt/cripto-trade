package com.marmitt.ctrade.application.service;

import com.marmitt.ctrade.domain.dto.OrderUpdateMessage;
import com.marmitt.ctrade.domain.dto.PriceUpdateMessage;
import com.marmitt.ctrade.domain.listener.OrderUpdateListener;
import com.marmitt.ctrade.domain.listener.PriceUpdateListener;
import com.marmitt.ctrade.domain.port.WebSocketPort;
import com.marmitt.ctrade.infrastructure.adapter.MockWebSocketAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;

@Service
@Slf4j
public class WebSocketService {
    
    private final WebSocketPort webSocketPort;
    private final List<PriceUpdateListener> priceUpdateListeners;
    private final List<OrderUpdateListener> orderUpdateListeners;
    
    public WebSocketService(WebSocketPort webSocketPort, 
                           List<PriceUpdateListener> priceUpdateListeners,
                           List<OrderUpdateListener> orderUpdateListeners) {
        this.webSocketPort = webSocketPort;
        this.priceUpdateListeners = priceUpdateListeners;
        this.orderUpdateListeners = orderUpdateListeners;
    }
    
    @PostConstruct
    public void init() {
        if (webSocketPort instanceof MockWebSocketAdapter mockAdapter) {
            mockAdapter.setWebSocketService(this);
            log.info("WebSocketService reference set in MockWebSocketAdapter");
        }
    }
    
    public void startConnection() {
        log.info("Starting WebSocket connection");
        webSocketPort.connect();
        webSocketPort.subscribeToOrderUpdates();
    }
    
    public void stopConnection() {
        log.info("Stopping WebSocket connection");
        webSocketPort.disconnect();
    }
    
    public void subscribeToTradingPair(String tradingPair) {
        log.info("Subscribing to price updates for: {}", tradingPair);
        webSocketPort.subscribeToPrice(tradingPair);
    }
    
    
    public void handlePriceUpdate(PriceUpdateMessage message) {
        log.info("Price update received: {} = {}", message.getTradingPair(), message.getPrice());
        
        priceUpdateListeners.forEach(listener -> {
            try {
                listener.onPriceUpdate(message);
            } catch (Exception e) {
                log.error("Error processing price update in listener {}: {}", 
                         listener.getClass().getSimpleName(), e.getMessage(), e);
            }
        });
    }
    
    public void handleOrderUpdate(OrderUpdateMessage message) {
        log.info("Order update received: {} status: {}", message.getOrderId(), message.getStatus());
        
        orderUpdateListeners.forEach(listener -> {
            try {
                listener.onOrderUpdate(message);
            } catch (Exception e) {
                log.error("Error processing order update in listener {}: {}", 
                         listener.getClass().getSimpleName(), e.getMessage(), e);
            }
        });
    }
}