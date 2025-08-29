//package com.marmitt.ctrade.infrastructure.exchange.mock;
//
//import com.marmitt.ctrade.domain.dto.PriceUpdateMessage;
//import com.marmitt.ctrade.domain.dto.OrderUpdateMessage;
//import com.marmitt.ctrade.infrastructure.config.MockExchangeProperties;
//import com.marmitt.ctrade.infrastructure.config.WebSocketProperties;
//import com.marmitt.ctrade.infrastructure.exchange.mock.strategy.FeedStrategyFactory;
//import com.marmitt.ctrade.infrastructure.websocket.*;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
//import org.springframework.stereotype.Component;
//
//import java.time.LocalDateTime;
//import java.util.Optional;
//import java.util.concurrent.ScheduledExecutorService;
//import java.util.concurrent.Executors;
//import java.util.concurrent.ScheduledFuture;
//import java.util.concurrent.TimeUnit;
//import java.util.concurrent.ThreadLocalRandom;
//
//@Component
//@ConditionalOnProperty(name = "websocket.exchange", havingValue = "MOCK", matchIfMissing = true)
//@Slf4j
//public class MockWebSocketAdapter extends AbstractWebSocketAdapter {
//
//    // Mock-specific dependencies
//    private final MockExchangeProperties mockProperties;
//    private final FeedStrategyFactory feedStrategyFactory;
//    private final MockExchangeAdapter mockExchangeAdapter;
//    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
//    private ScheduledFuture<?> priceUpdateTask;
//    private ScheduledFuture<?> orderUpdateTask;
//
//    // Feed strategy
//    private FeedStrategy feedStrategy;
//
//    public MockWebSocketAdapter(WebSocketProperties properties,
//                               WebSocketEventPublisher eventPublisher,
//                               ConnectionManager connectionManager,
//                               ConnectionStatsTracker statsTracker,
//                               MockExchangeProperties mockProperties,
//                               FeedStrategyFactory feedStrategyFactory,
//                               MockExchangeAdapter mockExchangeAdapter) {
//        super(eventPublisher, connectionManager, statsTracker, properties);
//        this.mockProperties = mockProperties;
//        this.feedStrategyFactory = feedStrategyFactory;
//        this.mockExchangeAdapter = mockExchangeAdapter;
//    }
//
//    @Override
//    protected void doConnect() {
//        log.info("Mock WebSocket connecting. Feed strategy should be executed.");
//
//        // Inicializa a estratégia de feed
//        if (feedStrategy == null) {
//            feedStrategy = feedStrategyFactory.createFeedStrategy();
//            feedStrategy.initialize();
//
//            // Registra a feedStrategy como listener do MockExchangeAdapter
//            mockExchangeAdapter.addOrderEventListener(feedStrategy);
//
//            log.info("Initialized feed strategy: {} and registered as order event listener",
//                feedStrategy.getStrategyName());
//        }
//
//        connectionManager.updateStatus(ConnectionStatus.CONNECTED);
//        statsTracker.updateLastConnectedAt(LocalDateTime.now());
//        startSimulators();
//    }
//
//    @Override
//    protected void doDisconnect() {
//        connectionManager.updateStatus(ConnectionStatus.DISCONNECTED);
//        stopSimulators();
//
//        // Limpa a estratégia de feed
//        if (feedStrategy != null) {
//            // Remove do listener do MockExchangeAdapter
//            mockExchangeAdapter.removeOrderEventListener(feedStrategy);
//            feedStrategy.cleanup();
//            feedStrategy = null;
//            log.info("Cleaned up feed strategy and removed order event listener");
//        }
//    }
//
//    @Override
//    public String getExchangeName() {
//        return "MOCK";
//    }
//
//
//    @Override
//    protected void doSubscribeToPrice(String tradingPair) {
//        // No additional logic needed for mock - subscription is handled by parent
//    }
//
//    @Override
//    protected void doSubscribeToOrderUpdates() {
//        // No additional logic needed for mock - subscription is handled by parent
//    }
//
//
//    private void startSimulators() {
//        if (connectionManager != null && connectionManager.isConnected()) {
//            startPriceUpdateSimulator();
//            startOrderUpdateSimulator();
//            log.info("Started message simulators");
//        }
//    }
//
//    private void startPriceUpdateSimulator() {
//        long baseDelay = mockProperties.getMessageDelay().getPriceUpdates();
//        long initialDelay = getRandomDelay(baseDelay);
//
//        priceUpdateTask = scheduler.scheduleWithFixedDelay(() -> {
//            try {
//                if (connectionManager.isConnected()) {
//                    simulatePriceUpdate();
//                }
//            } catch (Exception e) {
//                log.error("Error in price simulator: {}", e.getMessage(), e);
//            }
//        }, initialDelay, getRandomDelay(baseDelay), TimeUnit.MILLISECONDS);
//    }
//
//    private void startOrderUpdateSimulator() {
//        long baseDelay = mockProperties.getMessageDelay().getOrderUpdates();
//        long initialDelay = getRandomDelay(baseDelay);
//
//        orderUpdateTask = scheduler.scheduleWithFixedDelay(() -> {
//            try {
//                if (connectionManager.isConnected()) {
//                    simulateOrderUpdateFromStrategy();
//                }
//            } catch (Exception e) {
//                log.error("Error in order simulator: {}", e.getMessage(), e);
//            }
//        }, initialDelay, getRandomDelay(baseDelay), TimeUnit.MILLISECONDS);
//    }
//
//    private void stopSimulators() {
//        if (priceUpdateTask != null && !priceUpdateTask.isCancelled()) {
//            priceUpdateTask.cancel(false);
//        }
//        if (orderUpdateTask != null && !orderUpdateTask.isCancelled()) {
//            orderUpdateTask.cancel(false);
//        }
//        log.info("Stopped message simulators");
//    }
//
//    private void simulatePriceUpdate() {
//        if (feedStrategy == null) {
//            log.warn("Feed strategy not initialized, skipping price update");
//            return;
//        }
//
//        try {
//            Optional<PriceUpdateMessage> priceUpdate = feedStrategy.generateNextPriceUpdate();
//            if (priceUpdate.isPresent()) {
//                onPriceUpdate(priceUpdate.get());
//            } else {
//                // Verifica se ainda há dados disponíveis
//                if (!feedStrategy.hasMoreData()) {
//                    log.info("Feed strategy {} reports no more data available", feedStrategy.getStrategyName());
//                    terminateSimulation();
//                }
//            }
//        } catch (Exception e) {
//            log.error("Error generating price update from strategy {}: {}",
//                feedStrategy.getStrategyName(), e.getMessage(), e);
//        }
//    }
//
//    private void simulateOrderUpdateFromStrategy() {
//        if (feedStrategy == null) {
//            log.warn("Feed strategy not initialized, skipping order update");
//            return;
//        }
//
//        try {
//            Optional<OrderUpdateMessage> orderUpdate = feedStrategy.generateNextOrderUpdate();
//            if (orderUpdate.isPresent()) {
//                onOrderUpdate(orderUpdate.get());
//                log.debug("Processed order update from strategy {}: {}",
//                    feedStrategy.getStrategyName(), orderUpdate.get().getOrderId());
//            } else {
//                log.trace("No order updates available from strategy {}", feedStrategy.getStrategyName());
//            }
//        } catch (Exception e) {
//            log.error("Error generating order update from strategy {}: {}",
//                feedStrategy.getStrategyName(), e.getMessage(), e);
//        }
//    }
//
//    private long getRandomDelay(long baseDelay) {
//        // Para alta velocidade (< 100ms), não aplicar variação
//        if (baseDelay < 100) {
//            return baseDelay;
//        }
//
//        // Aplicar variação apenas para delays maiores
//        long minDelay = mockProperties.getMessageDelay().getMinDelay();
//        long maxDelay = mockProperties.getMessageDelay().getMaxDelay();
//        long halfRange = (maxDelay - minDelay) / 2;
//        long variation = ThreadLocalRandom.current().nextLong(-halfRange, halfRange + 1);
//        return Math.max(1, baseDelay + variation);
//    }
//
//    private void terminateSimulation() {
//        log.info("=== SIMULATION TERMINATION ===");
//        if (feedStrategy != null) {
//            log.info("Feed strategy {} completed execution", feedStrategy.getStrategyName());
//        }
//        log.info("Mock exchange simulation is now ending...");
//        log.info("===============================");
//
//        // Stop all simulators and disconnect
//        stopSimulators();
//        disconnect();
//    }
//
//}