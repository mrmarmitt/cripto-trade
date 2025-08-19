package com.marmitt.ctrade.infrastructure.exchange.binance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
public class BinanceTickerMessage {
    
    @JsonProperty("e")
    private String eventType;
    
    @JsonProperty("E")
    private long eventTime;
    
    @JsonProperty("s")
    private String symbol;
    
    @JsonProperty("c")
    private BigDecimal currentPrice;
    
    @JsonProperty("P")
    private BigDecimal priceChangePercent;
    
    @JsonProperty("p")
    private BigDecimal priceChange;
    
    @JsonProperty("w")
    private BigDecimal weightedAvgPrice;
    
    @JsonProperty("x")
    private BigDecimal prevClosePrice;
    
    @JsonProperty("Q")
    private BigDecimal lastQuantity;
    
    @JsonProperty("b")
    private BigDecimal bidPrice;
    
    @JsonProperty("B")
    private BigDecimal bidQty;
    
    @JsonProperty("a")
    private BigDecimal askPrice;
    
    @JsonProperty("A")
    private BigDecimal askQty;
    
    @JsonProperty("o")
    private BigDecimal openPrice;
    
    @JsonProperty("h")
    private BigDecimal highPrice;
    
    @JsonProperty("l")
    private BigDecimal lowPrice;
    
    @JsonProperty("v")
    private BigDecimal volume;
    
    @JsonProperty("q")
    private BigDecimal quoteVolume;
    
    @JsonProperty("O")
    private long openTime;
    
    @JsonProperty("C")
    private long closeTime;
    
    @JsonProperty("F")
    private long firstId;
    
    @JsonProperty("L")
    private long lastId;
    
    @JsonProperty("n")
    private long count;
}