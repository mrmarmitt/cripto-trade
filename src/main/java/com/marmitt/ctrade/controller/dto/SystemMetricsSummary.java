package com.marmitt.ctrade.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SystemMetricsSummary {
    
    private int totalPairsTracked;
    private int totalPriceUpdates;
    private double systemAverageVolatility;
    private LocalDateTime firstUpdateTime;
    private LocalDateTime lastUpdateTime;
}