package com.marmitt.ctrade.application.scheduler;

import com.marmitt.ctrade.application.service.PriceCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class PriceCacheCleanupScheduler {
    
    private final PriceCacheService priceCacheService;
    
    @Scheduled(fixedRateString = "${trading.price-cache.cleanup-interval-minutes}", timeUnit = TimeUnit.MINUTES)
    public void cleanupExpiredCacheEntries() {
        try {
            log.debug("Starting scheduled cleanup of expired price cache entries");
            
            int removedCount = priceCacheService.clearExpiredEntries();
            
            if (removedCount > 0) {
                log.info("Scheduled cleanup removed {} expired price cache entries", removedCount);
            } else {
                log.debug("Scheduled cleanup found no expired entries to remove");
            }
            
        } catch (Exception e) {
            log.error("Error during scheduled price cache cleanup", e);
        }
    }
}