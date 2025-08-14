package com.marmitt.ctrade.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PriceResponse {
    private String tradingPair;
    private BigDecimal price;
    private LocalDateTime timestamp;

    public PriceResponse(String tradingPair, BigDecimal price) {
        this.tradingPair = tradingPair;
        this.price = price;
        this.timestamp = LocalDateTime.now();
    }
}