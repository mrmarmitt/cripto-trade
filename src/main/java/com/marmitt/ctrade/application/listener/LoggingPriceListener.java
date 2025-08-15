package com.marmitt.ctrade.application.listener;

import com.marmitt.ctrade.domain.dto.PriceUpdateMessage;
import com.marmitt.ctrade.domain.listener.PriceUpdateListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class LoggingPriceListener implements PriceUpdateListener {
    
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    
    // Track previous prices for change detection
    private final Map<String, BigDecimal> previousPrices = new ConcurrentHashMap<>();
    
    @Override
    public void onPriceUpdate(PriceUpdateMessage message) {
        String tradingPair = message.getTradingPair();
        BigDecimal currentPrice = message.getPrice();
        LocalDateTime timestamp = message.getTimestamp();
        
        BigDecimal previousPrice = previousPrices.get(tradingPair);
        
        // Calculate change and percentage
        String changeInfo = "";
        if (previousPrice != null && !previousPrice.equals(BigDecimal.ZERO)) {
            BigDecimal change = currentPrice.subtract(previousPrice);
            BigDecimal changePercent = change.divide(previousPrice, 4, java.math.RoundingMode.HALF_UP)
                                           .multiply(BigDecimal.valueOf(100));
            
            String direction = change.compareTo(BigDecimal.ZERO) >= 0 ? "↗" : "↘";
            changeInfo = String.format(" %s %+.4f (%+.2f%%)", direction, change.doubleValue(), changePercent.doubleValue());
        }
        
        // Structured logging for audit trail
        log.info("PRICE_UPDATE | {} | {} | {} | prev: {} {}",
                timestamp.format(TIMESTAMP_FORMAT),
                tradingPair,
                currentPrice,
                previousPrice != null ? previousPrice : "N/A",
                changeInfo);
        
        // Log significant price movements (>5%)
        if (previousPrice != null && isSignificantChange(currentPrice, previousPrice, 5.0)) {
            log.warn("SIGNIFICANT_PRICE_MOVEMENT | {} | {} -> {} {}",
                    tradingPair,
                    previousPrice,
                    currentPrice,
                    changeInfo);
        }
        
        // Update previous price
        previousPrices.put(tradingPair, currentPrice);
    }
    
    private boolean isSignificantChange(BigDecimal current, BigDecimal previous, double thresholdPercent) {
        if (previous.equals(BigDecimal.ZERO)) return false;
        
        BigDecimal change = current.subtract(previous).abs();
        BigDecimal changePercent = change.divide(previous, 4, java.math.RoundingMode.HALF_UP)
                                        .multiply(BigDecimal.valueOf(100));
        
        return changePercent.doubleValue() >= thresholdPercent;
    }
    
    public void logPriceSummary(String tradingPair) {
        BigDecimal currentPrice = previousPrices.get(tradingPair);
        if (currentPrice != null) {
            log.info("PRICE_SUMMARY | {} | current: {}", tradingPair, currentPrice);
        } else {
            log.info("PRICE_SUMMARY | {} | no price data available", tradingPair);
        }
    }
    
    public void logAllPricesSummary() {
        log.info("=== CURRENT PRICES SUMMARY ===");
        if (previousPrices.isEmpty()) {
            log.info("No price data available");
        } else {
            previousPrices.forEach((pair, price) -> 
                log.info("{}: {}", pair, price));
        }
        log.info("===============================");
    }
}