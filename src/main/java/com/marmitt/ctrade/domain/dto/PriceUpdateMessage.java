package com.marmitt.ctrade.domain.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class PriceUpdateMessage {
    
    private String tradingPair;
    private BigDecimal price;
    private LocalDateTime timestamp;
}