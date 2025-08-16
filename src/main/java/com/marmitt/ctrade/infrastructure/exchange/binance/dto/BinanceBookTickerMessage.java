package com.marmitt.ctrade.infrastructure.exchange.binance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * DTO para mensagens de Book Ticker da Binance.
 * Representa os melhores preços de bid/ask do order book.
 */
@Data
public class BinanceBookTickerMessage {
    
    /**
     * Order book updateId
     */
    @JsonProperty("u")
    private Long updateId;
    
    /**
     * Symbol (trading pair)
     * Exemplo: "BTCUSDT"
     */
    @JsonProperty("s")
    private String symbol;
    
    /**
     * Best bid price
     */
    @JsonProperty("b")
    private BigDecimal bestBidPrice;
    
    /**
     * Best bid quantity  
     */
    @JsonProperty("B")
    private BigDecimal bestBidQuantity;
    
    /**
     * Best ask price
     */
    @JsonProperty("a")
    private BigDecimal bestAskPrice;
    
    /**
     * Best ask quantity
     */
    @JsonProperty("A")
    private BigDecimal bestAskQuantity;
}