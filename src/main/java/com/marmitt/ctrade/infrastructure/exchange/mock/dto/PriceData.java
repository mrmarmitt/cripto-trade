package com.marmitt.ctrade.infrastructure.exchange.mock.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class PriceData {
    private long timestamp;
    private String price;
    private String volume;
    
    public BigDecimal getPriceAsBigDecimal() {
        return new BigDecimal(price);
    }
    
    public BigDecimal getVolumeAsBigDecimal() {
        return new BigDecimal(volume);
    }
}