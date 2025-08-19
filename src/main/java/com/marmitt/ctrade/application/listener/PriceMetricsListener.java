package com.marmitt.ctrade.application.listener;

import com.marmitt.ctrade.application.service.PriceMetricsService;
import com.marmitt.ctrade.domain.dto.PriceUpdateMessage;
import com.marmitt.ctrade.domain.listener.PriceUpdateListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PriceMetricsListener implements PriceUpdateListener {
    
    private final PriceMetricsService priceMetricsService;
    
    @Override
    public void onPriceUpdate(PriceUpdateMessage message) {
        priceMetricsService.recordPriceUpdate(
            message.getTradingPair(),
            message.getPrice(),
            message.getTimestamp()
        );
        
        log.trace("Metrics recorded for price update: {} -> {}", 
                message.getTradingPair(), 
                message.getPrice());
    }
}