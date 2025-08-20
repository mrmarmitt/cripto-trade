package com.marmitt.ctrade.infrastructure.exchange.mock.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.ctrade.domain.port.TradingPairProvider;
import com.marmitt.ctrade.infrastructure.config.MockExchangeProperties;
import com.marmitt.ctrade.infrastructure.exchange.mock.dto.PriceData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class MockMarketDataLoader {
    
    private final MockExchangeProperties properties;
    private final ResourceLoader resourceLoader;
    private final TradingPairProvider tradingPairProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private Map<String, List<PriceData>> priceDataMap = new HashMap<>();
    private Map<String, Integer> currentIndexMap = new HashMap<>();
    private Map<String, Boolean> completedMap = new HashMap<>();
    
    @PostConstruct
    public void loadMarketData() {
        // Get active trading pairs from configuration
        List<String> activePairs = tradingPairProvider.getActiveTradingPairs();
        log.info("Loading market data for active trading pairs: {}", activePairs);
        
        for (String pair : activePairs) {
            loadPriceDataForPair(pair);
        }
        
        log.info("Successfully loaded market data for {} trading pairs from {}", 
            priceDataMap.size(), properties.getDataFolder());
    }
    
    private void loadPriceDataForPair(String tradingPair) {
        try {
            String fileName = tradingPair + ".json";
            String resourcePath = properties.getDataFolder() + fileName;
            Resource resource = resourceLoader.getResource(resourcePath);
            
            if (!resource.exists()) {
                log.warn("Market data file not found: {}", resourcePath);
                return;
            }
            
            List<PriceData> priceDataList = objectMapper.readValue(
                resource.getInputStream(), 
                new TypeReference<List<PriceData>>() {}
            );
            
            priceDataMap.put(tradingPair, priceDataList);
            currentIndexMap.put(tradingPair, 0);
            completedMap.put(tradingPair, false);
            
            log.debug("Loaded {} price points for {}", priceDataList.size(), tradingPair);
            
        } catch (IOException e) {
            log.error("Failed to load market data for {}: {}", tradingPair, e.getMessage());
        }
    }
    
    /**
     * Get the next price data for a trading pair in strict sequential mode
     */
    public PriceData getNextPriceData(String tradingPair) {
        List<PriceData> priceDataList = priceDataMap.get(tradingPair);
        if (priceDataList == null || priceDataList.isEmpty()) {
            log.warn("No price data available for {}", tradingPair);
            return null;
        }
        
        Integer currentIndex = currentIndexMap.get(tradingPair);
        if (currentIndex == null) {
            currentIndex = 0;
        }
        
        // Check if we've reached the end of the data
        if (currentIndex >= priceDataList.size()) {
            completedMap.put(tradingPair, true);
            log.info("Data consumption completed for trading pair: {}", tradingPair);
            return null;
        }
        
        PriceData priceData = priceDataList.get(currentIndex);
        currentIndexMap.put(tradingPair, currentIndex + 1);
        
        log.debug("Returning price data for {} at index {}: {}", tradingPair, currentIndex, priceData.getPrice());
        return priceData;
    }
    
    /**
     * Get current price for a trading pair without advancing the index
     */
    public PriceData getCurrentPriceData(String tradingPair) {
        List<PriceData> priceDataList = priceDataMap.get(tradingPair);
        if (priceDataList == null || priceDataList.isEmpty()) {
            return null;
        }
        
        Integer currentIndex = currentIndexMap.get(tradingPair);
        if (currentIndex == null) {
            currentIndex = 0;
        }
        
        // Ensure we don't go out of bounds
        if (currentIndex >= priceDataList.size()) {
            currentIndex = priceDataList.size() - 1;
        }
        
        return priceDataList.get(currentIndex);
    }
    
    public boolean hasMarketData(String tradingPair) {
        return priceDataMap.containsKey(tradingPair) && 
               !priceDataMap.get(tradingPair).isEmpty();
    }
    
    public Map<String, List<PriceData>> getAllMarketData() {
        return new HashMap<>(priceDataMap);
    }
    
    /**
     * Reset the index for a specific trading pair
     */
    public void resetIndex(String tradingPair) {
        currentIndexMap.put(tradingPair, 0);
        completedMap.put(tradingPair, false);
    }
    
    /**
     * Reset all indices to start from the beginning
     */
    public void resetAllIndices() {
        currentIndexMap.replaceAll((k, v) -> 0);
        completedMap.replaceAll((k, v) -> false);
    }
    
    /**
     * Check if all trading pairs have completed their data consumption
     */
    public boolean isAllDataConsumed() {
        if (completedMap.isEmpty()) {
            return false;
        }
        return completedMap.values().stream().allMatch(Boolean::booleanValue);
    }
    
    /**
     * Check if a specific trading pair has completed its data consumption
     */
    public boolean isDataConsumed(String tradingPair) {
        return completedMap.getOrDefault(tradingPair, false);
    }
    
    /**
     * Get the number of consumed trading pairs
     */
    public int getConsumedPairsCount() {
        return (int) completedMap.values().stream().mapToInt(consumed -> consumed ? 1 : 0).sum();
    }
    
    /**
     * Get the total number of trading pairs
     */
    public int getTotalPairsCount() {
        return priceDataMap.size();
    }
    
}