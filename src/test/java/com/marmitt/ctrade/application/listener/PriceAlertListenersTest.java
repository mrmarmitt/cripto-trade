package com.marmitt.ctrade.application.listener;

import com.marmitt.ctrade.application.service.PriceAlertService;
import com.marmitt.ctrade.application.service.PriceMetricsService;
import com.marmitt.ctrade.domain.dto.PriceUpdateMessage;
import com.marmitt.ctrade.domain.entity.PriceAlert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PriceAlertListenersTest {
    
    @Mock
    private PriceAlertService priceAlertService;
    
    @Mock
    private PriceMetricsService priceMetricsService;
    
    @InjectMocks
    private AlertPriceListener alertPriceListener;
    
    @InjectMocks
    private MetricsPriceListener metricsPriceListener;
    
    private LoggingPriceListener loggingPriceListener;
    
    @BeforeEach
    void setUp() {
        loggingPriceListener = new LoggingPriceListener();
    }
    
    @Test
    void shouldProcessPriceUpdateThroughMetricsListener() {
        // Given
        String tradingPair = "BTCUSD";
        BigDecimal price = new BigDecimal("45000.00");
        
        PriceUpdateMessage message = new PriceUpdateMessage();
        message.setTradingPair(tradingPair);
        message.setPrice(price);
        message.setTimestamp(LocalDateTime.now());
        
        // When
        metricsPriceListener.onPriceUpdate(message);
        
        // Then
        verify(priceMetricsService).recordPriceUpdate(eq(tradingPair), eq(price), any(LocalDateTime.class));
    }
    
    @Test
    void shouldProcessAlertPriceUpdate() {
        // Given
        String tradingPair = "ETHUSD";
        BigDecimal price = new BigDecimal("3100.00");
        
        PriceUpdateMessage message = new PriceUpdateMessage();
        message.setTradingPair(tradingPair);
        message.setPrice(price);
        message.setTimestamp(LocalDateTime.now());
        
        when(priceAlertService.checkAndTriggerAlerts(tradingPair, price))
                .thenReturn(List.of(new PriceAlert(tradingPair, new BigDecimal("3000"), PriceAlert.AlertType.ABOVE)));
        
        // When
        alertPriceListener.onPriceUpdate(message);
        
        // Then
        verify(priceAlertService).checkAndTriggerAlerts(tradingPair, price);
    }
    
    @Test
    void shouldLogPriceUpdates() {
        // Given
        String tradingPair = "BNBUSD";
        BigDecimal price = new BigDecimal("350.00");
        
        PriceUpdateMessage message = new PriceUpdateMessage();
        message.setTradingPair(tradingPair);
        message.setPrice(price);
        message.setTimestamp(LocalDateTime.now());
        
        // When - LoggingPriceListener doesn't throw exceptions
        loggingPriceListener.onPriceUpdate(message);
        
        // Then - should complete without exceptions
        // LoggingPriceListener behavior is verified through logs, not assertions
    }
    
    @Test
    void shouldHandleEmptyTriggeredAlertsGracefully() {
        // Given
        String tradingPair = "ADAUSD";
        BigDecimal price = new BigDecimal("0.45");
        
        PriceUpdateMessage message = new PriceUpdateMessage();
        message.setTradingPair(tradingPair);
        message.setPrice(price);
        message.setTimestamp(LocalDateTime.now());
        
        when(priceAlertService.checkAndTriggerAlerts(tradingPair, price))
                .thenReturn(List.of());
        
        // When
        alertPriceListener.onPriceUpdate(message);
        
        // Then - should handle gracefully without exceptions
        verify(priceAlertService).checkAndTriggerAlerts(tradingPair, price);
    }
}