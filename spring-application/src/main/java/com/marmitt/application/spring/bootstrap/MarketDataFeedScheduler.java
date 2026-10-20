package com.marmitt.application.spring.bootstrap;

import com.marmitt.application.spring.config.core.MarketDataFeedProperties;
import com.marmitt.core.ports.inbound.websocket.EnsureMarketDataFeedPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Confere periodicamente a demanda de market data dos runners contra o que está chegando (T56). Só
 * agenda; a regra é do core.
 */
@Slf4j
@Component
public class MarketDataFeedScheduler {

    private final EnsureMarketDataFeedPort ensureMarketDataFeed;
    private final MarketDataFeedProperties properties;

    public MarketDataFeedScheduler(EnsureMarketDataFeedPort ensureMarketDataFeed, MarketDataFeedProperties properties) {
        this.ensureMarketDataFeed = ensureMarketDataFeed;
        this.properties = properties;
    }

    @Scheduled(initialDelayString = "${ctrade.market-data.auto-feed.initial-delay-ms:5000}",
            fixedDelayString = "${ctrade.market-data.auto-feed.interval-ms:30000}")
    public void scheduledTick() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            ensureMarketDataFeed.ensure();
        } catch (RuntimeException e) {
            log.error("marketData: feed cycle failed - {}", e.getMessage(), e);
        }
    }
}
