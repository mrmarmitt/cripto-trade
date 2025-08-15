package com.marmitt.ctrade.domain.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class PriceAlert {
    
    private String id;
    private String tradingPair;
    private BigDecimal threshold;
    private AlertType alertType;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime triggeredAt;
    
    public PriceAlert(String tradingPair, BigDecimal threshold, AlertType alertType) {
        this.tradingPair = tradingPair;
        this.threshold = threshold;
        this.alertType = alertType;
        this.active = true;
        this.createdAt = LocalDateTime.now();
        this.id = generateId();
    }
    
    private String generateId() {
        return "ALERT_" + tradingPair + "_" + alertType + "_" + System.currentTimeMillis();
    }
    
    public boolean shouldTrigger(BigDecimal currentPrice) {
        if (!active) return false;
        
        return switch (alertType) {
            case ABOVE -> currentPrice.compareTo(threshold) > 0;
            case BELOW -> currentPrice.compareTo(threshold) < 0;
        };
    }
    
    public void trigger() {
        this.triggeredAt = LocalDateTime.now();
        this.active = false;
    }
    
    public enum AlertType {
        ABOVE, BELOW
    }
}