package com.marmitt.ctrade.infrastructure.exchange.mock;

import com.marmitt.ctrade.domain.dto.PriceUpdateMessage;
import com.marmitt.ctrade.domain.dto.OrderUpdateMessage;
import com.marmitt.ctrade.domain.entity.Order;
import com.marmitt.ctrade.domain.port.ExchangeWebSocketAdapter.ConnectionStatus;
import com.marmitt.ctrade.infrastructure.config.MockExchangeProperties;
import com.marmitt.ctrade.infrastructure.config.WebSocketProperties;
import com.marmitt.ctrade.infrastructure.exchange.mock.dto.PriceData;
import com.marmitt.ctrade.infrastructure.exchange.mock.service.MockMarketDataLoader;
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
import java.util.concurrent.ThreadLocalRandom;

@Component
@ConditionalOnProperty(name = "websocket.exchange", havingValue = "MOCK", matchIfMissing = true)
@Slf4j
public class MockWebSocketAdapter extends AbstractWebSocketAdapter {
    
    // Mock-specific dependencies
    private final Random random = new Random();
    private final MockExchangeProperties mockProperties;
    private final MockMarketDataLoader marketDataLoader;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private ScheduledFuture<?> priceUpdateTask;
    private ScheduledFuture<?> orderUpdateTask;
    
    public MockWebSocketAdapter(WebSocketProperties properties,
                               WebSocketEventPublisher eventPublisher,
                               ConnectionManager connectionManager,
                               ConnectionStatsTracker statsTracker,
                               MockExchangeProperties mockProperties,
                               MockMarketDataLoader marketDataLoader) {
        super(eventPublisher, connectionManager, statsTracker, properties);
        this.mockProperties = mockProperties;
        this.marketDataLoader = marketDataLoader;
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
        long baseDelay = mockProperties.getMessageDelay().getPriceUpdates();
        long initialDelay = getRandomDelay(baseDelay);
        
        priceUpdateTask = scheduler.scheduleWithFixedDelay(() -> {
            try {
                if (connectionManager.isConnected()) {
                    simulatePriceUpdate();
                }
            } catch (Exception e) {
                log.error("Error in price simulator: {}", e.getMessage(), e);
            }
        }, initialDelay, getRandomDelay(baseDelay), TimeUnit.MILLISECONDS);
    }
    
    private void startOrderUpdateSimulator() {
        long baseDelay = mockProperties.getMessageDelay().getOrderUpdates();
        long initialDelay = getRandomDelay(baseDelay);
        
        orderUpdateTask = scheduler.scheduleWithFixedDelay(() -> {
            try {
                if (connectionManager.isConnected()) {
                    simulateOrderUpdate();
                }
            } catch (Exception e) {
                log.error("Error in order simulator: {}", e.getMessage(), e);
            }
        }, initialDelay, getRandomDelay(baseDelay), TimeUnit.MILLISECONDS);
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
        // Get trading pairs from loaded market data (deterministic order)
        String[] availablePairs = marketDataLoader.getAllMarketData().keySet().toArray(new String[0]);
        if (availablePairs.length == 0) {
            log.warn("No market data available for price simulation");
            return;
        }
        
        // In strict mode, cycle through pairs in a deterministic order
        if (mockProperties.isStrictMode()) {
            // Use a simple counter to cycle through pairs
            int pairIndex = (int) (System.currentTimeMillis() / 1000) % availablePairs.length;
            String selectedPair = availablePairs[pairIndex];
            
            PriceData priceData = marketDataLoader.getNextPriceData(selectedPair);
            if (priceData != null) {
                PriceUpdateMessage message = new PriceUpdateMessage();
                message.setTradingPair(selectedPair);
                message.setPrice(priceData.getPriceAsBigDecimal());
                message.setTimestamp(LocalDateTime.now());
                
                log.debug("Strict mode price update: {} -> {} (from file)", selectedPair, priceData.getPrice());
                onPriceUpdate(message);
            } else {
                // Check if all data has been consumed
                if (marketDataLoader.isAllDataConsumed()) {
                    log.info("Simulation completed: All market data consumed ({}/{} trading pairs)", 
                        marketDataLoader.getConsumedPairsCount(), marketDataLoader.getTotalPairsCount());
                    terminateSimulation();
                    return;
                }
            }
        } else {
            // Legacy random mode (fallback)
            String randomPair = availablePairs[random.nextInt(availablePairs.length)];
            BigDecimal newPrice = generatePriceForPair(randomPair);
            
            PriceUpdateMessage message = new PriceUpdateMessage();
            message.setTradingPair(randomPair);
            message.setPrice(newPrice);
            message.setTimestamp(LocalDateTime.now());
            
            log.debug("Random mode price update: {} -> {}", randomPair, newPrice);
            onPriceUpdate(message);
        }
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
    
    private BigDecimal generatePriceForPair(String pair) {
        // Fallback method for non-strict mode
        PriceData currentPriceData = marketDataLoader.getCurrentPriceData(pair);
        if (currentPriceData != null) {
            BigDecimal basePrice = currentPriceData.getPriceAsBigDecimal();
            return generatePriceWithVolatility(basePrice);
        }
        
        // Final fallback to default prices
        return getDefaultPriceForPair(pair);
    }
    
    private BigDecimal generatePriceWithVolatility(BigDecimal basePrice) {
        double volatility = mockProperties.getPriceSimulation().getVolatility();
        double trendProbability = mockProperties.getPriceSimulation().getTrendProbability();
        double trendStrength = mockProperties.getPriceSimulation().getTrendStrength();
        
        // Determine if this should be a trending or random movement
        double variation;
        if (random.nextDouble() < trendProbability) {
            // Trending movement
            variation = (random.nextBoolean() ? 1 : -1) * trendStrength;
        } else {
            // Random walk
            variation = (random.nextDouble() - 0.5) * volatility;
        }
        
        BigDecimal variationAmount = basePrice.multiply(BigDecimal.valueOf(variation));
        return basePrice.add(variationAmount).setScale(8, RoundingMode.HALF_UP);
    }
    
    private BigDecimal getDefaultPriceForPair(String pair) {
        return switch (pair.toUpperCase()) {
            case "BTCUSDT", "BTC/USD", "BTCUSD" -> BigDecimal.valueOf(45000 + random.nextInt(10000));
            case "ETHUSDT", "ETH/USD", "ETHUSD" -> BigDecimal.valueOf(2500 + random.nextInt(1000));
            case "ADAUSDT", "ADA/USD", "ADAUSD" -> BigDecimal.valueOf(1 + random.nextDouble());
            default -> BigDecimal.valueOf(100 + random.nextInt(900));
        };
    }
    
    private long getRandomDelay(long baseDelay) {
        // Para alta velocidade (< 100ms), não aplicar variação
        if (baseDelay < 100) {
            return baseDelay;
        }
        
        // Aplicar variação apenas para delays maiores
        long minDelay = mockProperties.getMessageDelay().getMinDelay();
        long maxDelay = mockProperties.getMessageDelay().getMaxDelay();
        long halfRange = (maxDelay - minDelay) / 2;
        long variation = ThreadLocalRandom.current().nextLong(-halfRange, halfRange + 1);
        return Math.max(1, baseDelay + variation);
    }
    
    private void terminateSimulation() {
        log.info("=== SIMULATION TERMINATION ===");
        log.info("All market data files have been completely processed");
        log.info("Trading pairs completed: {}/{}", 
            marketDataLoader.getConsumedPairsCount(), marketDataLoader.getTotalPairsCount());
        log.info("Mock exchange simulation is now ending...");
        log.info("===============================");
        
        // Stop all simulators and disconnect
        stopSimulators();
        disconnect();
    }
    
}