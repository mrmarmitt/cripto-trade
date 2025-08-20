package com.marmitt.ctrade.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "mock.exchange")
public class MockExchangeProperties {
    
    private Feed feed = new Feed();
    
    // Convenience methods for backward compatibility
    public boolean isStrictMode() {
        return feed.getStrict().isEnable();
    }
    
    public String getDataFolder() {
        return isStrictMode() ? feed.getStrict().getDataFolder() : feed.getRandom().getDataFolder();
    }
    
    private MessageDelay messageDelay = new MessageDelay();
    private Orders orders = new Orders();
    private PriceSimulation priceSimulation = new PriceSimulation();
    private MarketConditions marketConditions = new MarketConditions();
    
    @Data
    public static class MessageDelay {
        private long priceUpdates = 2000;
        private long orderUpdates = 5000;
        private long minDelay = 500;
        private long maxDelay = 1000;
    }
    
    @Data
    public static class Orders {
        private double acceptanceRate = 0.8;
        private double executionRate = 0.7;
        private double priceMargin = 0.05;
        private double partialFillRate = 0.2;
    }
    
    @Data
    public static class PriceSimulation {
        private double volatility = 0.02;
        private double trendProbability = 0.1;
        private double trendStrength = 0.005;
        private boolean replayMode = false;
        private double replaySpeed = 1.0;
    }
    
    @Data
    public static class MarketConditions {
        private double slippageRate = 0.001;
        private double liquidityFactor = 1.0;
    }
    
    @Data
    public static class Feed {
        private StrictFeed strict = new StrictFeed();
        private RandomFeed random = new RandomFeed();
    }
    
    @Data
    public static class StrictFeed {
        private boolean enable = true;
        private String dataFolder = "classpath:mock-data/";
    }
    
    @Data
    public static class RandomFeed {
        private boolean enable = false;
        private String dataFolder = "classpath:mock-data/";
    }
}