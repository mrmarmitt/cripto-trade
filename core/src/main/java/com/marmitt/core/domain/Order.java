package com.marmitt.core.domain;

import com.marmitt.core.enums.OrderSide;
import com.marmitt.core.enums.OrderType;

import java.math.BigDecimal;
import java.time.Instant;

public record Order(
        String clientOrderId,
        Symbol symbol,
        OrderSide side,
        OrderType type,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal stopPrice,
        Instant timestamp
) {
    public static Order marketBuy(String clientOrderId, Symbol symbol, BigDecimal quantity) {
        return new Order(clientOrderId, symbol, OrderSide.BUY, OrderType.MARKET, 
                        quantity, null, null, Instant.now());
    }
    
    public static Order marketSell(String clientOrderId, Symbol symbol, BigDecimal quantity) {
        return new Order(clientOrderId, symbol, OrderSide.SELL, OrderType.MARKET, 
                        quantity, null, null, Instant.now());
    }
    
    public static Order limitBuy(String clientOrderId, Symbol symbol, BigDecimal quantity, BigDecimal price) {
        return new Order(clientOrderId, symbol, OrderSide.BUY, OrderType.LIMIT, 
                        quantity, price, null, Instant.now());
    }
    
    public static Order limitSell(String clientOrderId, Symbol symbol, BigDecimal quantity, BigDecimal price) {
        return new Order(clientOrderId, symbol, OrderSide.SELL, OrderType.LIMIT, 
                        quantity, price, null, Instant.now());
    }
    
    public boolean isBuy() {
        return OrderSide.BUY.equals(side);
    }
    
    public boolean isSell() {
        return OrderSide.SELL.equals(side);
    }
    
    public boolean isMarket() {
        return OrderType.MARKET.equals(type);
    }
    
    public boolean isLimit() {
        return OrderType.LIMIT.equals(type);
    }
}