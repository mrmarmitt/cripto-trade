//package com.marmitt.ctrade.infrastructure.exchange.mock.strategy;
//
//import com.marmitt.ctrade.domain.dto.OrderUpdateMessage;
//import com.marmitt.ctrade.domain.dto.PriceUpdateMessage;
//import com.marmitt.ctrade.domain.entity.Order;
//import com.marmitt.ctrade.infrastructure.config.MockExchangeProperties;
//import com.marmitt.ctrade.infrastructure.exchange.mock.dto.PriceData;
//import com.marmitt.ctrade.infrastructure.exchange.mock.service.MockMarketDataLoader;
//import com.marmitt.ctrade.infrastructure.exchange.mock.service.OrderUpdateConverter;
//import lombok.extern.slf4j.Slf4j;
//
//import java.math.BigDecimal;
//import java.math.RoundingMode;
//import java.time.LocalDateTime;
//import java.util.Optional;
//import java.util.Random;
//import java.util.concurrent.ArrayBlockingQueue;
//import java.util.concurrent.BlockingQueue;
//import java.util.concurrent.TimeUnit;
//
///**
// * Estratégia de feed random - gera dados aleatórios com volatilidade configurável.
// */
//@Slf4j
//public class RandomFeedStrategy implements FeedStrategy {
//
//    private final MockMarketDataLoader marketDataLoader;
//    private final MockExchangeProperties mockProperties;
//    private final Random random = new Random();
//    private final String[] availablePairs;
//
//    // Fila para armazenar order updates recebidos do MockExchangeAdapter
//    private final BlockingQueue<OrderUpdateMessage> orderUpdateQueue = new ArrayBlockingQueue<>(1000);
//
//    public RandomFeedStrategy(MockMarketDataLoader marketDataLoader, MockExchangeProperties mockProperties) {
//        this.marketDataLoader = marketDataLoader;
//        this.mockProperties = mockProperties;
//        this.availablePairs = marketDataLoader.getAllMarketData().keySet().toArray(new String[0]);
//    }
//
//    @Override
//    public Optional<PriceUpdateMessage> generateNextPriceUpdate() {
//        if (availablePairs.length == 0) {
//            log.warn("No market data available for random feed");
//            return Optional.empty();
//        }
//
//        String randomPair = availablePairs[random.nextInt(availablePairs.length)];
//        BigDecimal newPrice = generatePriceForPair(randomPair);
//
//        PriceUpdateMessage message = new PriceUpdateMessage();
//        message.setTradingPair(randomPair);
//        message.setPrice(newPrice);
//        message.setTimestamp(LocalDateTime.now());
//
//        log.debug("Random feed price update: {} -> {}", randomPair, newPrice);
//        return Optional.of(message);
//    }
//
//    @Override
//    public boolean hasMoreData() {
//        return true; // Random feed nunca acaba
//    }
//
//    @Override
//    public String getStrategyName() {
//        return "RANDOM";
//    }
//
//    @Override
//    public void initialize() {
//        log.info("Initialized random feed strategy with {} trading pairs", availablePairs.length);
//    }
//
//    @Override
//    public void cleanup() {
//        orderUpdateQueue.clear();
//        log.info("Random feed strategy cleanup completed");
//    }
//
//    private BigDecimal generatePriceForPair(String pair) {
//        // Tenta usar dados base dos arquivos mock
//        PriceData currentPriceData = marketDataLoader.getCurrentPriceData(pair);
//        if (currentPriceData != null) {
//            BigDecimal basePrice = currentPriceData.getPriceAsBigDecimal();
//            return generatePriceWithVolatility(basePrice);
//        }
//
//        // Fallback para preços padrão
//        return getDefaultPriceForPair(pair);
//    }
//
//    private BigDecimal generatePriceWithVolatility(BigDecimal basePrice) {
//        double volatility = mockProperties.getPriceSimulation().getVolatility();
//        double trendProbability = mockProperties.getPriceSimulation().getTrendProbability();
//        double trendStrength = mockProperties.getPriceSimulation().getTrendStrength();
//
//        double variation;
//        if (random.nextDouble() < trendProbability) {
//            // Movimento de tendência
//            variation = (random.nextBoolean() ? 1 : -1) * trendStrength;
//        } else {
//            // Random walk
//            variation = (random.nextDouble() - 0.5) * volatility;
//        }
//
//        BigDecimal variationAmount = basePrice.multiply(BigDecimal.valueOf(variation));
//        return basePrice.add(variationAmount).setScale(8, RoundingMode.HALF_UP);
//    }
//
//    private BigDecimal getDefaultPriceForPair(String pair) {
//        return switch (pair.toUpperCase()) {
//            case "BTCUSDT", "BTC/USD", "BTCUSD" -> BigDecimal.valueOf(45000 + random.nextInt(10000));
//            case "ETHUSDT", "ETH/USD", "ETHUSD" -> BigDecimal.valueOf(2500 + random.nextInt(1000));
//            case "ADAUSDT", "ADA/USD", "ADAUSD" -> BigDecimal.valueOf(1 + random.nextDouble());
//            default -> BigDecimal.valueOf(100 + random.nextInt(900));
//        };
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