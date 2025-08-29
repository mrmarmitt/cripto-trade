package com.marmitt.ctrade.application.strategy;

import com.marmitt.ctrade.application.service.PriceCacheService;
import com.marmitt.ctrade.domain.entity.MarketData;
import com.marmitt.ctrade.domain.entity.Portfolio;
import com.marmitt.ctrade.domain.entity.TradingPair;
import com.marmitt.ctrade.domain.valueobject.StrategySignal;
import com.marmitt.ctrade.domain.valueobject.SignalType;
import com.marmitt.ctrade.infrastructure.config.StrategyProperties;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
public class PairTradingStrategy extends AbstractTradingStrategy {
    
    private static final String STRATEGY_NAME = "PairTradingStrategy";
    
    private final List<BigDecimal> spreadHistory = new ArrayList<>();
    private final int maxHistorySize;
    private final double upperThreshold;
    private final double lowerThreshold;
    private final BigDecimal tradingAmount;
    @Getter
    private final TradingPair pair1;
    @Getter
    private final TradingPair pair2;
    
    public PairTradingStrategy() {
        this(new StrategyProperties.StrategyConfig());
    }
    
    public PairTradingStrategy(StrategyProperties.StrategyConfig config) {
        super(STRATEGY_NAME, config);
        
        this.maxHistorySize = getIntegerParameter("maxHistorySize", 50);
        this.upperThreshold = getDoubleParameter("upperThreshold", 2.0);
        this.lowerThreshold = getDoubleParameter("lowerThreshold", -2.0);
        this.tradingAmount = BigDecimal.valueOf(getDoubleParameter("tradingAmount", 100.0));
        
        String pair1Symbol = getStringParameter("pair1", "BTCUSDT");
        String pair2Symbol = getStringParameter("pair2", "ETHUSDT");
        
        this.pair1 = parseTradingPair(pair1Symbol);
        this.pair2 = parseTradingPair(pair2Symbol);
        
        log.info("PairTradingStrategy initialized with pairs: {} and {}, thresholds: [{}, {}]", 
                pair1Symbol, pair2Symbol, lowerThreshold, upperThreshold);
    }
    
    @Override
    public StrategySignal analyze(MarketData marketData, Portfolio portfolio) {
        if (!isEnabled()) {
            return new StrategySignal(SignalType.HOLD, null, null, null, "Strategy is disabled", getStrategyName());
        }
        
        if (!marketData.hasPriceFor(pair1) || !marketData.hasPriceFor(pair2)) {
            log.debug("Missing price data for pair trading analysis");
            log.debug("Looking for pair1: {}", pair1);
            log.debug("Looking for pair2: {}", pair2);
            log.debug("HasPriceFor pair1: {}", marketData.hasPriceFor(pair1));
            log.debug("HasPriceFor pair2: {}", marketData.hasPriceFor(pair2));
            return new StrategySignal(SignalType.HOLD, null, null, null, 
                String.format("Missing price data for %s or %s", pair1.getSymbol(), pair2.getSymbol()), 
                getStrategyName());
        }
        
        BigDecimal price1 = marketData.getPriceFor(pair1);
        BigDecimal price2 = marketData.getPriceFor(pair2);
        
        BigDecimal spread = calculateSpread(price1, price2);
        updateSpreadHistory(spread);
        
        if (spreadHistory.size() < 10) {
            log.debug("Insufficient history for pair trading analysis. Current size: {}", spreadHistory.size());
            return new StrategySignal(SignalType.HOLD, null, null, null, 
                String.format("Insufficient history: %d/10 samples. %s=%s, %s=%s", 
                    spreadHistory.size(), pair1.getSymbol(), price1, pair2.getSymbol(), price2), 
                getStrategyName());
        }
        
        double zScore = calculateZScore(spread);
        
        log.info("📈 PairTrading Analysis - Spread: {}, Z-Score: {:.2f}, Thresholds: [{}, {}], History: {}", 
                spread, zScore, lowerThreshold, upperThreshold, spreadHistory.size());
        
        // More aggressive reversal: close positions when z-score approaches neutral zone
        if (Math.abs(zScore) < 0.5) {
            log.info("⚡ NEUTRAL ZONE - Z-score {:.2f} close to zero, generating SELL to close positions", zScore);
            return createSellPair1BuyPair2Signal(price1, price2, zScore);
        }
        
        if (zScore > upperThreshold) {
            log.info("🔴 SELL Signal - Z-score {:.2f} > threshold {} - Mean reversion expected", zScore, upperThreshold);
            return createSellPair1BuyPair2Signal(price1, price2, zScore);
        } else if (zScore < lowerThreshold) {
            log.info("🟢 BUY Signal - Z-score {:.2f} < threshold {} - Opening new position", zScore, lowerThreshold);
            return createBuyPair1SellPair2Signal(price1, price2, zScore);
        }
        
        log.debug("⚪ HOLD Signal - Z-score {:.2f} within thresholds [{}, {}]", zScore, lowerThreshold, upperThreshold);
        return new StrategySignal(SignalType.HOLD, null, null, null, 
            String.format("Z-score %.2f within thresholds [%.2f, %.2f]. %s=%s, %s=%s, spread=%s", 
                zScore, lowerThreshold, upperThreshold, pair1.getSymbol(), price1, pair2.getSymbol(), price2, spread), 
            getStrategyName());
    }
    
    private BigDecimal calculateSpread(BigDecimal price1, BigDecimal price2) {
        if (price2.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("Price2 cannot be zero for spread calculation");
        }
        return price1.divide(price2, 8, RoundingMode.HALF_UP);
    }
    
    private void updateSpreadHistory(BigDecimal spread) {
        spreadHistory.add(spread);
        if (spreadHistory.size() > maxHistorySize) {
            spreadHistory.remove(0);
        }
    }
    
    private double calculateZScore(BigDecimal currentSpread) {
        if (spreadHistory.size() < 2) {
            return 0.0;
        }
        
        double mean = calculateMean();
        double stdDev = calculateStandardDeviation(mean);
        
        if (stdDev == 0.0) {
            return 0.0;
        }
        
        return (currentSpread.doubleValue() - mean) / stdDev;
    }
    
    private double calculateMean() {
        return spreadHistory.stream()
                .mapToDouble(BigDecimal::doubleValue)
                .average()
                .orElse(0.0);
    }
    
    private double calculateStandardDeviation(double mean) {
        double variance = spreadHistory.stream()
                .mapToDouble(BigDecimal::doubleValue)
                .map(spread -> Math.pow(spread - mean, 2))
                .average()
                .orElse(0.0);
        
        return Math.sqrt(variance);
    }
    
    private StrategySignal createSellPair1BuyPair2Signal(BigDecimal price1, BigDecimal price2, double zScore) {
        // In true pair trading, when spread is high we should:
        // 1. SELL the overperforming asset (pair1)
        // 2. BUY the underperforming asset (pair2)
        // For simplicity, we'll create signals for pair1 first, but log both actions
        
        BigDecimal quantity1 = tradingAmount.divide(price1, 8, RoundingMode.HALF_UP);
        
        String reason = String.format("🔄 Pair trading REVERSION: Z-score %.2f > %.2f. Spread too HIGH - SELL %s @ %s (expecting mean reversion)", 
                zScore, upperThreshold, pair1.getSymbol(), price1);
        
        log.info("🔴 Pair Trading Signal: SELL {} {} @ {} | Should also BUY {} @ {} (spread reversion)", 
                pair1.getSymbol(), quantity1, price1, pair2.getSymbol(), price2);
        
        return StrategySignal.sell(pair1, quantity1, price1, reason, getStrategyName());
    }
    
    private StrategySignal createBuyPair1SellPair2Signal(BigDecimal price1, BigDecimal price2, double zScore) {
        // In true pair trading, when spread is low we should:
        // 1. BUY the underperforming asset (pair1) 
        // 2. SELL the overperforming asset (pair2)
        // For simplicity, we'll create signals for pair1 first, but log both actions
        
        BigDecimal quantity1 = tradingAmount.divide(price1, 8, RoundingMode.HALF_UP);
        
        String reason = String.format("🔄 Pair trading DIVERGENCE: Z-score %.2f < %.2f. Spread too LOW - BUY %s @ %s (expecting divergence)", 
                zScore, lowerThreshold, pair1.getSymbol(), price1);
        
        log.info("🟢 Pair Trading Signal: BUY {} {} @ {} | Should also SELL {} @ {} (spread divergence)", 
                pair1.getSymbol(), quantity1, price1, pair2.getSymbol(), price2);
        
        return StrategySignal.buy(pair1, quantity1, price1, reason, getStrategyName());
    }
    
    public double getCurrentZScore() {
        if (spreadHistory.isEmpty()) {
            return 0.0;
        }
        BigDecimal currentSpread = spreadHistory.getLast();
        return calculateZScore(currentSpread);
    }
    
    public int getHistorySize() {
        return spreadHistory.size();
    }
    
    private TradingPair parseTradingPair(String tradingPairString) {
        if (tradingPairString == null || tradingPairString.isEmpty()) {
            throw new IllegalArgumentException("Trading pair string cannot be null or empty");
        }
        
        // Handle formats like "BTCUSDT", "BTC-USDT", "BTC/USDT"
        String cleanPair = tradingPairString.replace("-", "").replace("/", "").toUpperCase();
        
        // Common trading pairs mapping
        if (cleanPair.endsWith("USDT")) {
            String baseCurrency = cleanPair.substring(0, cleanPair.length() - 4);
            return new TradingPair(baseCurrency, "USDT");
        } else if (cleanPair.endsWith("BTC")) {
            String baseCurrency = cleanPair.substring(0, cleanPair.length() - 3);
            return new TradingPair(baseCurrency, "BTC");
        } else if (cleanPair.endsWith("ETH")) {
            String baseCurrency = cleanPair.substring(0, cleanPair.length() - 3);
            return new TradingPair(baseCurrency, "ETH");
        } else if (cleanPair.endsWith("BNB")) {
            String baseCurrency = cleanPair.substring(0, cleanPair.length() - 3);
            return new TradingPair(baseCurrency, "BNB");
        } else if (cleanPair.endsWith("BUSD")) {
            String baseCurrency = cleanPair.substring(0, cleanPair.length() - 4);
            return new TradingPair(baseCurrency, "BUSD");
        } else {
            // Default fallback - assume last 3 characters are quote currency
            if (cleanPair.length() >= 6) {
                String baseCurrency = cleanPair.substring(0, cleanPair.length() - 3);
                String quoteCurrency = cleanPair.substring(cleanPair.length() - 3);
                return new TradingPair(baseCurrency, quoteCurrency);
            } else {
                throw new IllegalArgumentException("Unable to parse trading pair: " + tradingPairString);
            }
        }
    }

}