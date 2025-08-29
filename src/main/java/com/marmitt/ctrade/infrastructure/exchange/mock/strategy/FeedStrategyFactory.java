//package com.marmitt.ctrade.infrastructure.exchange.mock.strategy;
//
//import com.marmitt.ctrade.domain.port.TradingPairProvider;
//import com.marmitt.ctrade.domain.strategy.StreamProcessingStrategy;
//import com.marmitt.ctrade.infrastructure.config.MockExchangeProperties;
//import com.marmitt.ctrade.infrastructure.config.WebSocketProperties;
//import com.marmitt.ctrade.infrastructure.exchange.mock.service.MockMarketDataLoader;
//import com.marmitt.ctrade.infrastructure.exchange.strategy.StreamProcessingStrategyRegistry;
//import com.marmitt.ctrade.infrastructure.websocket.ConnectionManager;
//import com.marmitt.ctrade.infrastructure.websocket.ConnectionStatsTracker;
//import com.marmitt.ctrade.infrastructure.websocket.WebSocketEventPublisher;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Component;
//
///**
// * Factory para criar estratégias de feed baseado na configuração.
// */
//@Slf4j
//@Component
//public class FeedStrategyFactory {
//
//    private final MockMarketDataLoader marketDataLoader;
//    private final MockExchangeProperties mockProperties;
//    private final StreamProcessingStrategyRegistry strategyRegistry;
//    private final WebSocketEventPublisher eventPublisher;
//    private final ConnectionManager connectionManager;
//    private final ConnectionStatsTracker statsTracker;
//    private final WebSocketProperties webSocketProperties;
//    private final TradingPairProvider tradingPairProvider;
//
//    public FeedStrategyFactory(MockMarketDataLoader marketDataLoader,
//                              MockExchangeProperties mockProperties,
//                              StreamProcessingStrategyRegistry strategyRegistry,
//                              WebSocketEventPublisher eventPublisher,
//                              ConnectionManager connectionManager,
//                              ConnectionStatsTracker statsTracker,
//                              WebSocketProperties webSocketProperties,
//                              TradingPairProvider tradingPairProvider) {
//        this.marketDataLoader = marketDataLoader;
//        this.mockProperties = mockProperties;
//        this.strategyRegistry = strategyRegistry;
//        this.eventPublisher = eventPublisher;
//        this.connectionManager = connectionManager;
//        this.statsTracker = statsTracker;
//        this.webSocketProperties = webSocketProperties;
//        this.tradingPairProvider = tradingPairProvider;
//    }
//
//    /**
//     * Cria a estratégia de feed apropriada baseada na configuração.
//     */
//    public FeedStrategy createFeedStrategy() {
//        log.info("Feed strategy configuration: strict={}, random={}, real={}",
//            mockProperties.getFeed().getStrict().isEnable(),
//            mockProperties.getFeed().getRandom().isEnable(),
//            mockProperties.getFeed().getReal() != null ? mockProperties.getFeed().getReal().isEnable() : "null"
//        );
//
//        if (mockProperties.getFeed().getReal() != null) {
//            log.info("Real feed details: url={}, exchange={}",
//                mockProperties.getFeed().getReal().getUrl(),
//                mockProperties.getFeed().getReal().getExchange());
//        }
//
//        if (mockProperties.getFeed().getStrict().isEnable()) {
//            log.info("Creating STRICT feed strategy");
//            return new StrictFeedStrategy(marketDataLoader);
//
//        } else if (mockProperties.getFeed().getRandom().isEnable()) {
//            log.info("Creating RANDOM feed strategy");
//            return new RandomFeedStrategy(marketDataLoader, mockProperties);
//
//        } else if (mockProperties.getFeed().getReal() != null && mockProperties.getFeed().getReal().isEnable()) {
//            log.info("Creating REAL feed WebSocket adapter for exchange: {}", mockProperties.getFeed().getReal().getExchange());
//
//            // Obtém a estratégia de processamento para a exchange
//            StreamProcessingStrategy streamProcessingStrategy = getStreamProcessingStrategy(
//                mockProperties.getFeed().getReal().getExchange()
//            );
//
//            // Cria o RealFeedWebSocketAdapter
//            return new RealFeedStrategy(
//                eventPublisher,
//                connectionManager,
//                statsTracker,
//                webSocketProperties,
//                mockProperties.getFeed().getReal().getUrl(),
//                mockProperties.getFeed().getReal().getExchange(),
//                streamProcessingStrategy,
//                tradingPairProvider
//            );
//
//        } else {
//            log.warn("No feed strategy enabled, defaulting to STRICT mode");
//            return new StrictFeedStrategy(marketDataLoader);
//        }
//    }
//
//    /**
//     * Obtém a estratégia de processamento de stream para a exchange especificada.
//     */
//    private StreamProcessingStrategy getStreamProcessingStrategy(String exchangeName) {
//        StreamProcessingStrategy strategy = strategyRegistry.getStrategy(exchangeName);
//        if (strategy == null) {
//            log.error("No StreamProcessingStrategy found for exchange: {}. Supported exchanges: {}",
//                exchangeName, String.join(", ", strategyRegistry.getSupportedExchanges()));
//        }
//        return strategy;
//    }
//}