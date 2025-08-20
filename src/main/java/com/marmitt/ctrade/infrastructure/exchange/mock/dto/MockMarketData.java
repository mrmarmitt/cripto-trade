package com.marmitt.ctrade.infrastructure.exchange.mock.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class MockMarketData {
    private String symbol;
    private String priceChange;
    private String priceChangePercent;
    private String weightedAvgPrice;
    private String prevClosePrice;
    private String lastPrice;
    private String lastQty;
    private String bidPrice;
    private String askPrice;
    private String openPrice;
    private String highPrice;
    private String lowPrice;
    private String volume;
    private String quoteVolume;
    private long openTime;
    private long closeTime;
    private long firstId;
    private long lastId;
    private int count;
    private List<PriceTimestamp> timestamps;
    
    @Data
    public static class PriceTimestamp {
        private long time;
        private String price;
        
        public BigDecimal getPriceAsBigDecimal() {
            return new BigDecimal(price);
        }
    }
    
    public BigDecimal getLastPriceAsBigDecimal() {
        return new BigDecimal(lastPrice);
    }
    
    public BigDecimal getBidPriceAsBigDecimal() {
        return new BigDecimal(bidPrice);
    }
    
    public BigDecimal getAskPriceAsBigDecimal() {
        return new BigDecimal(askPrice);
    }
}