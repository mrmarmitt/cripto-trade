package com.marmitt.ctrade.infrastructure.exchange.mock;

import com.marmitt.ctrade.domain.dto.PriceUpdateMessage;
import com.marmitt.ctrade.domain.dto.OrderUpdateMessage;
import com.marmitt.ctrade.domain.entity.Order;
import com.marmitt.ctrade.domain.port.ExchangeWebSocketAdapter.ConnectionStatus;
import com.marmitt.ctrade.infrastructure.config.WebSocketProperties;
import com.marmitt.ctrade.infrastructure.websocket.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "websocket.exchange", havingValue = "MOCK", matchIfMissing = true)
@Slf4j
public class MockWebSocketAdapter extends AbstractWebSocketAdapter {
    
    // Mock-specific dependencies
    private final Random random = new Random();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private ScheduledFuture<?> priceUpdateTask;
    private ScheduledFuture<?> orderUpdateTask;
    
    public MockWebSocketAdapter(WebSocketProperties properties,
                               WebSocketEventPublisher eventPublisher,
                               ConnectionManager connectionManager,
                               ConnectionStatsTracker statsTracker) {
        super(eventPublisher, connectionManager, statsTracker, properties);
    }

    @Override
    protected void doConnect() {
        log.info("Mock WebSocket connecting to: {}", properties.getUrl());
        log.info("Connection timeout: {}, Max retries: {}", 
                properties.getConnectionTimeout(), 
                properties.getMaxRetries());
        
        connectionManager.updateStatus(ConnectionStatus.CONNECTED);
        statsTracker.updateLastConnectedAt(LocalDateTime.now());
        startSimulators();
    }
    
    @Override
    protected void doDisconnect() {
        connectionManager.updateStatus(ConnectionStatus.DISCONNECTED);
        stopSimulators();
    }
    
    @Override
    public String getExchangeName() {
        return "MOCK";
    }
    
    
    @Override
    protected void doSubscribeToPrice(String tradingPair) {
        // No additional logic needed for mock - subscription is handled by parent
    }
    
    @Override
    protected void doSubscribeToOrderUpdates() {
        // No additional logic needed for mock - subscription is handled by parent
    }
    
    
    private void startSimulators() {
        if (connectionManager != null && connectionManager.isConnected()) {
            startPriceUpdateSimulator();
            startOrderUpdateSimulator();
            log.info("Started message simulators");
        }
    }
    
    private void startPriceUpdateSimulator() {
        priceUpdateTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                if (connectionManager.isConnected()) {
                    simulatePriceUpdate();
                }
            } catch (Exception e) {
                log.error("Error in price simulator: {}", e.getMessage(), e);
            }
        }, 1, 2, TimeUnit.SECONDS);
    }
    
    private void startOrderUpdateSimulator() {
        orderUpdateTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                if (connectionManager.isConnected()) {
                    simulateOrderUpdate();
                }
            } catch (Exception e) {
                log.error("Error in order simulator: {}", e.getMessage(), e);
            }
        }, 2, 5, TimeUnit.SECONDS);
    }
    
    private void stopSimulators() {
        if (priceUpdateTask != null && !priceUpdateTask.isCancelled()) {
            priceUpdateTask.cancel(false);
        }
        if (orderUpdateTask != null && !orderUpdateTask.isCancelled()) {
            orderUpdateTask.cancel(false);
        }
        log.info("Stopped message simulators");
    }
    
    private void simulatePriceUpdate() {
        // Simulate some common trading pairs
        String[] commonPairs = {"BTCUSDT", "ETHUSDT", "ADAUSDT"};
        String randomPair = commonPairs[random.nextInt(commonPairs.length)];
        
        BigDecimal basePrice = getBasePriceForPair(randomPair);
        BigDecimal variation = basePrice.multiply(BigDecimal.valueOf((random.nextDouble() - 0.5) * 0.02)); // ±1% variation
        BigDecimal newPrice = basePrice.add(variation).setScale(2, RoundingMode.HALF_UP);
        
        PriceUpdateMessage message = new PriceUpdateMessage();
        message.setTradingPair(randomPair);
        message.setPrice(newPrice);
        message.setTimestamp(LocalDateTime.now());
        
        log.debug("Simulating price update: {} -> {}", randomPair, newPrice);
        onPriceUpdate(message); // Publica evento e atualiza stats automaticamente
    }
    
    private void simulateOrderUpdate() {
        String[] orderIds = {"ORD001", "ORD002", "ORD003", "ORD004", "ORD005"};
        Order.OrderStatus[] statuses = {Order.OrderStatus.FILLED, Order.OrderStatus.CANCELLED, Order.OrderStatus.PARTIALLY_FILLED};
        
        String orderId = orderIds[random.nextInt(orderIds.length)];
        Order.OrderStatus status = statuses[random.nextInt(statuses.length)];
        
        OrderUpdateMessage message = new OrderUpdateMessage();
        message.setOrderId(orderId);
        message.setStatus(status);
        message.setTimestamp(LocalDateTime.now());
        
        log.debug("Simulating order update: {} -> {}", orderId, status);
        onOrderUpdate(message); // Publica evento e atualiza stats automaticamente
    }
    
    private BigDecimal getBasePriceForPair(String pair) {
        return switch (pair.toUpperCase()) {
            case "BTCUSDT", "BTC/USD", "BTCUSD" -> BigDecimal.valueOf(45000 + random.nextInt(10000));
            case "ETHUSDT", "ETH/USD", "ETHUSD" -> BigDecimal.valueOf(2500 + random.nextInt(1000));
            case "ADAUSDT", "ADA/USD", "ADAUSD" -> BigDecimal.valueOf(1 + random.nextDouble());
            default -> BigDecimal.valueOf(100 + random.nextInt(900));
        };
    }
    
}