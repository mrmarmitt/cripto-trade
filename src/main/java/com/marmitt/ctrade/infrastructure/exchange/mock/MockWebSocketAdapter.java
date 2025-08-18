package com.marmitt.ctrade.infrastructure.exchange.mock;

import com.marmitt.ctrade.domain.dto.PriceUpdateMessage;
import com.marmitt.ctrade.domain.dto.OrderUpdateMessage;
import com.marmitt.ctrade.domain.entity.Order;
import com.marmitt.ctrade.infrastructure.config.WebSocketProperties;
import com.marmitt.ctrade.infrastructure.websocket.AbstractWebSocketAdapter;
import com.marmitt.ctrade.infrastructure.websocket.ReconnectionStrategy;
import com.marmitt.ctrade.infrastructure.websocket.WebSocketCircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Random;
import java.util.concurrent.ScheduledFuture;

@Component
@ConditionalOnProperty(name = "websocket.exchange", havingValue = "MOCK", matchIfMissing = true)
@Slf4j
public class MockWebSocketAdapter extends AbstractWebSocketAdapter {
    
    // Mock-specific dependencies
    private final Random random = new Random();
    private ScheduledFuture<?> priceUpdateTask;
    private ScheduledFuture<?> orderUpdateTask;
    
    public MockWebSocketAdapter(WebSocketProperties webSocketProperties,
                               ReconnectionStrategy reconnectionStrategy) {
        super(null,
                null,
                null,
                null);
    }

    @Override
    protected void doConnect() {
        log.info("Connection timeout: {}, Max retries: {}", 
                properties.getConnectionTimeout(), 
                properties.getMaxRetries());
        updateConnectionStatus(ConnectionStatus.CONNECTED);
        statsTracker.updateLastConnectedAt(LocalDateTime.now());
        startSimulators();
    }
    
    @Override
    protected void doDisconnect() {
        stopSimulators();
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
        // Usar um TaskScheduler injetado pelo Spring
        if (connectionManager != null) {
            // Para o mock, vamos usar um approach mais simples com threads
            startPriceUpdateSimulator();
            startOrderUpdateSimulator();
            log.info("Started message simulators");
        }
    }
    
    private void startPriceUpdateSimulator() {
        Thread priceSimulator = new Thread(() -> {
            while (isConnected()) {
                try {
                    simulatePriceUpdate();
                    Thread.sleep(2000); // 2 seconds
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("Error in price simulator: {}", e.getMessage(), e);
                }
            }
        });
        priceSimulator.setDaemon(true);
        priceSimulator.setName("mock-price-simulator");
        priceSimulator.start();
    }
    
    private void startOrderUpdateSimulator() {
        Thread orderSimulator = new Thread(() -> {
            while (isConnected()) {
                try {
                    simulateOrderUpdate();
                    Thread.sleep(5000); // 5 seconds
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("Error in order simulator: {}", e.getMessage(), e);
                }
            }
        });
        orderSimulator.setDaemon(true);
        orderSimulator.setName("mock-order-simulator");
        orderSimulator.start();
    }
    
    private void stopSimulators() {
        // Os simulators param automaticamente quando isConnected() retorna false
        log.info("Simulators will stop when connection status changes");
    }
    
    private void simulatePriceUpdate() {
        if (!getSubscribedPairs().isEmpty()) {
            String[] pairs = getSubscribedPairs().toArray(new String[0]);
            String randomPair = pairs[random.nextInt(pairs.length)];
            
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
    }
    
    private void simulateOrderUpdate() {
        if (isOrderUpdatesSubscribed()) {
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
    }
    
    private BigDecimal getBasePriceForPair(String pair) {
        return switch (pair.toUpperCase()) {
            case "BTC/USD", "BTCUSD" -> BigDecimal.valueOf(45000 + random.nextInt(10000));
            case "ETH/USD", "ETHUSD" -> BigDecimal.valueOf(2500 + random.nextInt(1000));
            case "BNB/USD", "BNBUSD" -> BigDecimal.valueOf(300 + random.nextInt(100));
            default -> BigDecimal.valueOf(100 + random.nextInt(900));
        };
    }
    
    @Override
    public String getExchangeName() {
        return "MOCK";
    }

}