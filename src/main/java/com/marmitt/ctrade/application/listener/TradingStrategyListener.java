package com.marmitt.ctrade.application.listener;

import com.marmitt.ctrade.application.service.PriceCacheService;
import com.marmitt.ctrade.application.service.TradingOrchestrator;
import com.marmitt.ctrade.domain.dto.PriceUpdateMessage;
import com.marmitt.ctrade.domain.entity.MarketData;
import com.marmitt.ctrade.domain.entity.TradingPair;
import com.marmitt.ctrade.domain.listener.PriceUpdateListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class TradingStrategyListener implements PriceUpdateListener {
    
    private final TradingOrchestrator tradingOrchestrator;
    private final PriceCacheService priceCacheService;
    
    @Override
    public void onPriceUpdate(PriceUpdateMessage message) {
        try {
            log.debug("Processing price update for trading strategies: {} = {}", 
                    message.getTradingPair(), message.getPrice());
            
            // Update the price cache first
            TradingPair updatedPair = parseTradingPair(message.getTradingPair());
            LocalDateTime timestamp = message.getTimestamp() != null ? 
                    message.getTimestamp() : LocalDateTime.now();
            
            priceCacheService.updatePrice(updatedPair.getSymbol(), message.getPrice(), timestamp);
            
            // Create comprehensive MarketData using cached prices
            MarketData marketData = createComprehensiveMarketData(updatedPair, message.getPrice(), timestamp);
            
            if (marketData != null) {
                tradingOrchestrator.executeStrategies(marketData);
                log.debug("Successfully triggered strategy execution for {}", message.getTradingPair());
            } else {
                log.warn("Failed to create comprehensive MarketData for: {}", message);
            }
            
        } catch (Exception e) {
            log.error("Error processing price update for trading strategies: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Creates comprehensive MarketData using cached prices for common trading pairs
     */
    private MarketData createComprehensiveMarketData(TradingPair updatedPair, BigDecimal updatedPrice, LocalDateTime timestamp) {
        try {
            Map<TradingPair, BigDecimal> currentPrices = new HashMap<>();
            
            // Add the updated pair
            currentPrices.put(updatedPair, updatedPrice);
            
            // Add common trading pairs from cache
            String[] commonPairs = {"BTC/USDT", "ETH/USDT", "BNB/USDT", "ADA/USDT", "SOL/USDT", "DOT/USDT"};
            
            for (String pairSymbol : commonPairs) {
                try {
                    TradingPair pair = new TradingPair(pairSymbol);
                    
                    // Skip if this is the updated pair (already added)
                    if (pair.equals(updatedPair)) {
                        continue;
                    }
                    
                    Optional<BigDecimal> cachedPrice = priceCacheService.getLatestPrice(pairSymbol);
                    if (cachedPrice.isPresent()) {
                        currentPrices.put(pair, cachedPrice.get());
                        log.trace("Added cached price for {}: {}", pairSymbol, cachedPrice.get());
                    } else {
                        log.trace("No cached price available for {}", pairSymbol);
                    }
                } catch (Exception e) {
                    log.trace("Error adding cached price for {}: {}", pairSymbol, e.getMessage());
                }
            }
            
            // Create MarketData with all available prices
            MarketData marketData = new MarketData();
            marketData.setCurrentPrices(currentPrices);
            marketData.setVolumes24h(new HashMap<>()); // Empty volumes for now
            marketData.setTimestamp(timestamp);
            
            log.debug("Created comprehensive MarketData with {} price entries", currentPrices.size());
            
            return marketData;
            
        } catch (Exception e) {
            log.error("Error creating comprehensive MarketData: {}", e.getMessage(), e);
            return null;
        }
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
            // This may not always be correct, but provides a reasonable default
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