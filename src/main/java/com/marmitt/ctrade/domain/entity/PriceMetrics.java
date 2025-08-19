package com.marmitt.ctrade.domain.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Data
public class PriceMetrics {
    
    private final String tradingPair;
    private final AtomicInteger updateCount = new AtomicInteger(0);
    private final AtomicLong totalVolume = new AtomicLong(0);
    
    private volatile BigDecimal currentPrice = BigDecimal.ZERO;
    private volatile BigDecimal highestPrice = BigDecimal.ZERO;
    private volatile BigDecimal lowestPrice = BigDecimal.valueOf(Double.MAX_VALUE);
    private volatile BigDecimal priceSum = BigDecimal.ZERO;
    
    private volatile LocalDateTime firstUpdateTime;
    private volatile LocalDateTime lastUpdateTime;
    private volatile LocalDateTime highestPriceTime;
    private volatile LocalDateTime lowestPriceTime;
    
    public PriceMetrics(String tradingPair) {
        this.tradingPair = tradingPair;
    }
    
    public synchronized void updatePrice(BigDecimal newPrice, LocalDateTime timestamp) {
        if (firstUpdateTime == null) {
            firstUpdateTime = timestamp;
            lowestPrice = newPrice;
            highestPrice = newPrice;
        }
        
        currentPrice = newPrice;
        lastUpdateTime = timestamp;
        updateCount.incrementAndGet();
        priceSum = priceSum.add(newPrice);
        
        // Update highest price
        if (newPrice.compareTo(highestPrice) > 0) {
            highestPrice = newPrice;
            highestPriceTime = timestamp;
        }
        
        // Update lowest price  
        if (newPrice.compareTo(lowestPrice) < 0) {
            lowestPrice = newPrice;
            lowestPriceTime = timestamp;
        }
    }
    
    public BigDecimal getAveragePrice() {
        int count = updateCount.get();
        if (count == 0) return BigDecimal.ZERO;
        return priceSum.divide(BigDecimal.valueOf(count), 8, RoundingMode.HALF_UP);
    }
    
    public BigDecimal getPriceRange() {
        if (highestPrice.equals(BigDecimal.ZERO)) return BigDecimal.ZERO;
        return highestPrice.subtract(lowestPrice);
    }
    
    public double getVolatility() {
        BigDecimal average = getAveragePrice();
        BigDecimal range = getPriceRange();
        if (average.equals(BigDecimal.ZERO)) return 0.0;
        
        return range.divide(average, 4, RoundingMode.HALF_UP)
                   .multiply(BigDecimal.valueOf(100))
                   .doubleValue();
    }
    
    public long getUpdateFrequencyPerMinute() {
        if (firstUpdateTime == null || lastUpdateTime == null) return 0;
        
        long minutes = java.time.Duration.between(firstUpdateTime, lastUpdateTime).toMinutes();
        if (minutes == 0) return updateCount.get();
        
        return updateCount.get() / minutes;
    }
}