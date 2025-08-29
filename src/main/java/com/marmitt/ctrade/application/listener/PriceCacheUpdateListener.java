package com.marmitt.ctrade.application.listener;

import com.marmitt.ctrade.application.service.PriceCacheService;
import com.marmitt.ctrade.domain.dto.PriceUpdateMessage;
import com.marmitt.ctrade.domain.listener.PriceUpdateListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PriceCacheUpdateListener implements PriceUpdateListener {
    
    private final PriceCacheService priceCacheService;
    
    @Override
    public void onPriceUpdate(PriceUpdateMessage priceUpdate) {
        log.info("Received price update via WebSocket: {} = {}", 
                priceUpdate.getTradingPair(), priceUpdate.getPrice());
        
        try {
            // Store price with original format (BTCUSDT) for consistent cache access
            priceCacheService.updatePrice(
                priceUpdate.getTradingPair(),
                priceUpdate.getPrice(),
                priceUpdate.getTimestamp()
            );
            
            log.debug("Successfully updated price cache for: {}", priceUpdate.getTradingPair());
            
        } catch (Exception e) {
            log.error("Failed to update price cache for {}: {}", 
                     priceUpdate.getTradingPair(), e.getMessage(), e);
        }
    }
}