package com.marmitt.ctrade.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HealthCheckResponse {
    
    private String status;
    private LocalDateTime timestamp;
    private CacheHealthInfo cache;
    private WebSocketHealthInfo webSocket;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CacheHealthInfo {
        private String status;
        private int tradingPairs;
        private int totalEntries;
        private String ttlMinutes;
        private int maxHistorySize;
    }
    
    @Data
    @NoArgsConstructor 
    @AllArgsConstructor
    public static class WebSocketHealthInfo {
        private String status;
        private boolean connected;
        private int subscribedPairs;
        private boolean orderUpdatesSubscribed;
    }
}