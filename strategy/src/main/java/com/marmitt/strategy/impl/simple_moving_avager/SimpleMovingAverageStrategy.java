package com.marmitt.strategy.impl.simple_moving_avager;

import com.marmitt.core.ports.outbound.strategy.TradingStrategy;
import com.marmitt.core.domain.StrategyInput;
import com.marmitt.core.domain.StrategyOutput;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class SimpleMovingAverageStrategy implements TradingStrategy {
    
    private static final String STRATEGY_NAME = "SimpleMovingAverageStrategy";
    private static final String STRATEGY_VERSION = "1.0.0";
    
    private boolean enabled = true;
    private final SimpleMovingAverageConfig config;
    private final List<BigDecimal> priceHistory = new ArrayList<>();
    
    public SimpleMovingAverageStrategy() {
        this(SimpleMovingAverageConfig.defaultConfig());
    }
    
    public SimpleMovingAverageStrategy(SimpleMovingAverageConfig config) {
        this.config = config;
    }

    @Override
    public StrategyOutput executeStrategy(StrategyInput inputData) {
        if (!enabled) {
            return StrategyOutput.hold(STRATEGY_NAME, inputData.symbol(), "Strategy is disabled");
        }
        
        if (inputData.currentPrice() == null) {
            return StrategyOutput.hold(STRATEGY_NAME, inputData.symbol(), "No current price available");
        }
        
        // Adiciona o preço atual ao histórico
        priceHistory.add(inputData.currentPrice());
        
        // Mantém apenas os últimos N preços
        if (priceHistory.size() > config.movingAveragePeriod()) {
            priceHistory.removeFirst();
        }
        
        // Precisa de pelo menos o período completo para calcular
        if (priceHistory.size() < config.movingAveragePeriod()) {
            return StrategyOutput.hold(STRATEGY_NAME, inputData.symbol(), 
                String.format("Insufficient data: %d/%d prices", priceHistory.size(), config.movingAveragePeriod()));
        }
        
        BigDecimal movingAverage = calculateMovingAverage();
        BigDecimal currentPrice = inputData.currentPrice();
        BigDecimal priceDeviation = calculatePriceDeviation(currentPrice, movingAverage);
        
        String reasoning = String.format("Price: %s, MA(%d): %s, Deviation: %.2f%%", 
                                        currentPrice, config.movingAveragePeriod(), movingAverage, 
                                        priceDeviation.multiply(BigDecimal.valueOf(100)).doubleValue());
        
        // Decisão baseada no desvio da média móvel
        if (priceDeviation.compareTo(config.sellThreshold()) >= 0) {
            // Preço muito acima da média - VENDER
            return StrategyOutput.sell(STRATEGY_NAME, inputData.symbol(), 
                                           config.tradingQuantity(), currentPrice, 
                                           reasoning + " - Price above MA threshold");
        } else if (priceDeviation.compareTo(config.buyThreshold()) <= 0) {
            // Preço muito abaixo da média - COMPRAR
            return StrategyOutput.buy(STRATEGY_NAME, inputData.symbol(), 
                                          config.tradingQuantity(), currentPrice, 
                                          reasoning + " - Price below MA threshold");
        } else {
            // Preço próximo da média - HOLD
            return StrategyOutput.hold(STRATEGY_NAME, inputData.symbol(), 
                                           reasoning + " - Price near MA");
        }
    }
    
    private BigDecimal calculateMovingAverage() {
        BigDecimal sum = priceHistory.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(priceHistory.size()), 8, RoundingMode.HALF_UP);
    }
    
    private BigDecimal calculatePriceDeviation(BigDecimal currentPrice, BigDecimal movingAverage) {
        if (movingAverage.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return currentPrice.subtract(movingAverage)
                .divide(movingAverage, 8, RoundingMode.HALF_UP);
    }

    @Override
    public String getStrategyName() {
        return STRATEGY_NAME;
    }

    @Override
    public String getStrategyVersion() {
        return STRATEGY_VERSION;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public SimpleMovingAverageConfig getConfig() {
        return config;
    }
    
    public int getCurrentHistorySize() {
        return priceHistory.size();
    }
    
    public List<BigDecimal> getPriceHistory() {
        return List.copyOf(priceHistory);
    }
    
    public void clearHistory() {
        priceHistory.clear();
    }
}