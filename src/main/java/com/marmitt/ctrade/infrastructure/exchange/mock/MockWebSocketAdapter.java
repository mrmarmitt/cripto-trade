package com.marmitt.ctrade.infrastructure.exchange.mock;

import com.marmitt.ctrade.domain.dto.PriceUpdateMessage;
import com.marmitt.ctrade.domain.dto.OrderUpdateMessage;
import com.marmitt.ctrade.domain.entity.Order;
import com.marmitt.ctrade.application.service.WebSocketService;
import com.marmitt.ctrade.domain.port.WebSocketPort;
import com.marmitt.ctrade.infrastructure.config.WebSocketProperties;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;

@Component
@ConditionalOnProperty(name = "websocket.exchange", havingValue = "MOCK", matchIfMissing = true)
@Slf4j
public class MockWebSocketAdapter implements WebSocketPort {
    
    private final WebSocketProperties webSocketProperties;
    private final TaskScheduler taskScheduler;
    @Setter
    private WebSocketService webSocketService;
    private boolean connected = false;
    private final Set<String> subscribedPairs = new HashSet<>();
    @Getter
    private boolean orderUpdatesSubscribed = false;
    private final Random random = new Random();
    private ScheduledFuture<?> priceUpdateTask;
    private ScheduledFuture<?> orderUpdateTask;
    
    public MockWebSocketAdapter(WebSocketProperties webSocketProperties, TaskScheduler taskScheduler) {
        this.webSocketProperties = webSocketProperties;
        this.taskScheduler = taskScheduler;
    }

    @Override
    public void connect() {
        log.info("Mock WebSocket connecting to: {}", webSocketProperties.getUrl());
        log.info("Connection timeout: {}, Max retries: {}", 
                webSocketProperties.getConnectionTimeout(), 
                webSocketProperties.getMaxRetries());
        connected = true;
        startSimulators();
        log.info("Mock WebSocket connected successfully");
    }
    
    @Override
    public void disconnect() {
        log.info("Mock WebSocket disconnecting...");
        stopSimulators();
        connected = false;
        subscribedPairs.clear();
        orderUpdatesSubscribed = false;
        log.info("Mock WebSocket disconnected");
    }
    
    @Override
    public boolean isConnected() {
        return connected;
    }
    
    @Override
    public void subscribeToPrice(String tradingPair) {
        if (!connected) {
            log.warn("Cannot subscribe to {}: WebSocket not connected", tradingPair);
            return;
        }
        
        subscribedPairs.add(tradingPair);
        log.info("Mock WebSocket subscribed to price updates for: {}", tradingPair);
    }
    
    @Override
    public void subscribeToOrderUpdates() {
        if (!connected) {
            log.warn("Cannot subscribe to order updates: WebSocket not connected");
            return;
        }
        
        orderUpdatesSubscribed = true;
        log.info("Mock WebSocket subscribed to order updates");
    }
    
    public Set<String> getSubscribedPairs() {
        return new HashSet<>(subscribedPairs);
    }
    
    private void startSimulators() {
        if (taskScheduler != null && webSocketService != null) {
            priceUpdateTask = taskScheduler.scheduleWithFixedDelay(
                this::simulatePriceUpdate, 
                java.time.Duration.ofSeconds(2)
            );
            orderUpdateTask = taskScheduler.scheduleWithFixedDelay(
                this::simulateOrderUpdate, 
                java.time.Duration.ofSeconds(5)
            );
            log.info("Started message simulators");
        }
    }
    
    private void stopSimulators() {
        if (priceUpdateTask != null && !priceUpdateTask.isCancelled()) {
            priceUpdateTask.cancel(false);
            log.info("Stopped price update simulator");
        }
        if (orderUpdateTask != null && !orderUpdateTask.isCancelled()) {
            orderUpdateTask.cancel(false);
            log.info("Stopped order update simulator");
        }
    }
    
    private void simulatePriceUpdate() {
        if (!subscribedPairs.isEmpty() && webSocketService != null) {
            String[] pairs = subscribedPairs.toArray(new String[0]);
            String randomPair = pairs[random.nextInt(pairs.length)];
            
            BigDecimal basePrice = getBasePriceForPair(randomPair);
            BigDecimal variation = basePrice.multiply(BigDecimal.valueOf((random.nextDouble() - 0.5) * 0.02)); // ±1% variation
            BigDecimal newPrice = basePrice.add(variation).setScale(2, RoundingMode.HALF_UP);
            
            PriceUpdateMessage message = new PriceUpdateMessage();
            message.setTradingPair(randomPair);
            message.setPrice(newPrice);
            message.setTimestamp(LocalDateTime.now());
            
            log.debug("Simulating price update: {} -> {}", randomPair, newPrice);
            webSocketService.handlePriceUpdate(message);
        }
    }
    
    private void simulateOrderUpdate() {
        if (orderUpdatesSubscribed && webSocketService != null) {
            String[] orderIds = {"ORD001", "ORD002", "ORD003", "ORD004", "ORD005"};
            Order.OrderStatus[] statuses = {Order.OrderStatus.FILLED, Order.OrderStatus.CANCELLED, Order.OrderStatus.PARTIALLY_FILLED};
            
            String orderId = orderIds[random.nextInt(orderIds.length)];
            Order.OrderStatus status = statuses[random.nextInt(statuses.length)];
            
            OrderUpdateMessage message = new OrderUpdateMessage();
            message.setOrderId(orderId);
            message.setStatus(status);
            message.setTimestamp(LocalDateTime.now());
            
            log.debug("Simulating order update: {} -> {}", orderId, status);
            webSocketService.handleOrderUpdate(message);
        }
    }
    
    private BigDecimal getBasePriceForPair(String pair) {
        return switch (pair.toUpperCase()) {
            case "BTC/USD", "BTCUSD" -> BigDecimal.valueOf(45000 + random.nextInt(10000));
            case "ETH/USD", "ETHUSD" -> BigDecimal.valueOf(2500 + random.nextInt(1000));
            case "BNB/USD", "BNBUSD" -> BigDecimal.valueOf(300 + random.nextInt(100));
            default -> BigDecimal.valueOf(100 + random.nextInt(900));
        };
    }

}