package com.marmitt.ctrade.application.strategy;

import com.marmitt.ctrade.domain.entity.MarketData;
import com.marmitt.ctrade.domain.entity.Portfolio;
import com.marmitt.ctrade.domain.entity.TradingPair;
import com.marmitt.ctrade.domain.valueobject.StrategySignal;
import com.marmitt.ctrade.domain.valueobject.SignalType;
import com.marmitt.ctrade.infrastructure.config.StrategyProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class PairTradingStrategy extends AbstractTradingStrategy {
    
    private static final String STRATEGY_NAME = "PairTradingStrategy";
    
    private final List<BigDecimal> spreadHistory = new ArrayList<>();
    private final int maxHistorySize;
    private final double upperThreshold;
    private final double lowerThreshold;
    private final BigDecimal tradingAmount;
    private final TradingPair pair1;
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
        
        String pair1Symbol = getStringParameter("pair1", "BTC/USDT");
        String pair2Symbol = getStringParameter("pair2", "ETH/USDT");
        
        this.pair1 = new TradingPair(pair1Symbol);
        this.pair2 = new TradingPair(pair2Symbol);
        
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
        
        log.debug("Pair trading analysis - Spread: {}, Z-Score: {}, Thresholds: [{}, {}]", 
                spread, zScore, lowerThreshold, upperThreshold);
        
        if (zScore > upperThreshold) {
            return createSellPair1BuyPair2Signal(price1, price2, zScore);
        } else if (zScore < lowerThreshold) {
            return createBuyPair1SellPair2Signal(price1, price2, zScore);
        }
        
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
        BigDecimal quantity1 = tradingAmount.divide(price1, 8, RoundingMode.HALF_UP);
        
        String reason = String.format("Pair trading: Spread above upper threshold (z-score: %.2f). Selling %s, buying %s", 
                zScore, pair1.getSymbol(), pair2.getSymbol());
        
        return StrategySignal.sell(pair1, quantity1, price1, reason, getStrategyName());
    }
    
    private StrategySignal createBuyPair1SellPair2Signal(BigDecimal price1, BigDecimal price2, double zScore) {
        BigDecimal quantity1 = tradingAmount.divide(price1, 8, RoundingMode.HALF_UP);
        
        String reason = String.format("Pair trading: Spread below lower threshold (z-score: %.2f). Buying %s, selling %s", 
                zScore, pair1.getSymbol(), pair2.getSymbol());
        
        return StrategySignal.buy(pair1, quantity1, price1, reason, getStrategyName());
    }
    
    public double getCurrentZScore() {
        if (spreadHistory.isEmpty()) {
            return 0.0;
        }
        BigDecimal currentSpread = spreadHistory.get(spreadHistory.size() - 1);
        return calculateZScore(currentSpread);
    }
    
    public int getHistorySize() {
        return spreadHistory.size();
    }
    
    public TradingPair getPair1() {
        return pair1;
    }
    
    public TradingPair getPair2() {
        return pair2;
    }
}