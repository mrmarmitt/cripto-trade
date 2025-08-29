//package com.marmitt.ctrade.infrastructure.exchange.mock.strategy;
//
//import com.marmitt.ctrade.domain.dto.OrderUpdateMessage;
//import com.marmitt.ctrade.domain.dto.PriceUpdateMessage;
//import com.marmitt.ctrade.domain.entity.Order;
//import com.marmitt.ctrade.infrastructure.exchange.mock.dto.PriceData;
//import com.marmitt.ctrade.infrastructure.exchange.mock.service.MockMarketDataLoader;
//import com.marmitt.ctrade.infrastructure.exchange.mock.service.OrderUpdateConverter;
//import lombok.extern.slf4j.Slf4j;
//
//import java.time.LocalDateTime;
//import java.util.Optional;
//import java.util.concurrent.ArrayBlockingQueue;
//import java.util.concurrent.BlockingQueue;
//import java.util.concurrent.TimeUnit;
//
///**
// * Estratégia de feed strict - usa dados exatos dos arquivos mock-data.
// */
//@Slf4j
//public class StrictFeedStrategy implements FeedStrategy {
//
//    private final MockMarketDataLoader marketDataLoader;
//    private final String[] availablePairs;
//
//    // Fila para armazenar order updates recebidos do MockExchangeAdapter
//    private final BlockingQueue<OrderUpdateMessage> orderUpdateQueue = new ArrayBlockingQueue<>(1000);
//
//    public StrictFeedStrategy(MockMarketDataLoader marketDataLoader) {
//        this.marketDataLoader = marketDataLoader;
//        this.availablePairs = marketDataLoader.getAllMarketData().keySet().toArray(new String[0]);
//    }
//
//    @Override
//    public Optional<PriceUpdateMessage> generateNextPriceUpdate() {
//        if (availablePairs.length == 0) {
//            log.warn("No market data available for strict feed");
//            return Optional.empty();
//        }
//
//        // Usa contador determinístico para ciclar pelos pares
//        int pairIndex = (int) (System.currentTimeMillis() / 1000) % availablePairs.length;
//        String selectedPair = availablePairs[pairIndex];
//
//        PriceData priceData = marketDataLoader.getNextPriceData(selectedPair);
//        if (priceData != null) {
//            PriceUpdateMessage message = new PriceUpdateMessage();
//            message.setTradingPair(selectedPair);
//            message.setPrice(priceData.getPriceAsBigDecimal());
//            message.setTimestamp(LocalDateTime.now());
//
//            log.debug("Strict feed price update: {} -> {} (from file)", selectedPair, priceData.getPrice());
//            return Optional.of(message);
//        }
//
//        return Optional.empty();
//    }
//
//    @Override
//    public boolean hasMoreData() {
//        return !marketDataLoader.isAllDataConsumed();
//    }
//
//    @Override
//    public String getStrategyName() {
//        return "STRICT";
//    }
//
//    @Override
//    public void initialize() {
//        log.info("Initialized strict feed strategy with {} trading pairs", availablePairs.length);
//    }
//
//    @Override
//    public void cleanup() {
//        orderUpdateQueue.clear();
//        log.info("Strict feed strategy cleanup completed");
//    }
//
//    @Override
//    public Optional<OrderUpdateMessage> generateNextOrderUpdate() {
//        try {
//            // Tenta pegar uma mensagem da fila com timeout de 50ms
//            OrderUpdateMessage orderUpdate = orderUpdateQueue.poll(50, TimeUnit.MILLISECONDS);
//            return Optional.ofNullable(orderUpdate);
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//            log.warn("Interrupted while waiting for order update");
//            return Optional.empty();
//        }
//    }
//
//    // Implementações do OrderEventListener
//
//    @Override
//    public void onOrderUpdated(Order order) {
//        OrderUpdateMessage message = OrderUpdateConverter.convertToOrderUpdate(order);
//
//        if (!orderUpdateQueue.offer(message)) {
//            log.warn("Order update queue full, dropping message for order: {}", order.getId());
//        } else {
//            log.debug("Queued order update for order {}: status={}", order.getId(), order.getStatus());
//        }
//    }
//
//    @Override
//    public void onOrderCancelled(Order order) {
//        // onOrderUpdated já será chamado, então não precisamos duplicar aqui
//        log.debug("Order cancelled: {}", order.getId());
//    }
//
//    @Override
//    public void onOrderFilled(Order order) {
//        // onOrderUpdated já será chamado, então não precisamos duplicar aqui
//        log.debug("Order filled: {}", order.getId());
//    }
//}