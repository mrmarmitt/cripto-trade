package com.marmitt.ctrade.application.listener;

import com.marmitt.ctrade.application.service.TradingOrchestrator;
import com.marmitt.ctrade.domain.dto.PriceUpdateMessage;
import com.marmitt.ctrade.domain.entity.MarketData;
import com.marmitt.ctrade.domain.entity.TradingPair;
import com.marmitt.ctrade.domain.listener.PriceUpdateListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class TradingStrategyListener implements PriceUpdateListener {
    
    private final TradingOrchestrator tradingOrchestrator;
    
    @Override
    public void onPriceUpdate(PriceUpdateMessage message) {
        try {
            log.debug("Processing price update for trading strategies: {} = {}", 
                    message.getTradingPair(), message.getPrice());
            
            MarketData marketData = convertToMarketData(message);
            
            if (marketData != null) {
                tradingOrchestrator.executeStrategies(marketData);
                log.debug("Successfully triggered strategy execution for {}", message.getTradingPair());
            } else {
                log.warn("Failed to convert PriceUpdateMessage to MarketData: {}", message);
            }
            
        } catch (Exception e) {
            log.error("Error processing price update for trading strategies: {}", e.getMessage(), e);
        }
    }
    
    private MarketData convertToMarketData(PriceUpdateMessage message) {
        if (message == null || message.getTradingPair() == null || message.getPrice() == null) {
            log.warn("Invalid PriceUpdateMessage: {}", message);
            return null;
        }
        
        try {
            TradingPair tradingPair = parseTradingPair(message.getTradingPair());
            LocalDateTime timestamp = message.getTimestamp() != null ? 
                    message.getTimestamp() : LocalDateTime.now();
            
            return new MarketData(tradingPair, message.getPrice(), timestamp);
            
        } catch (Exception e) {
            log.error("Error converting PriceUpdateMessage to MarketData: {}", e.getMessage(), e);
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