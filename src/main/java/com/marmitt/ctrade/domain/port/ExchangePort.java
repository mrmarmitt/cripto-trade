package com.marmitt.ctrade.domain.port;

import com.marmitt.ctrade.domain.entity.Order;
import com.marmitt.ctrade.domain.entity.TradingPair;
import com.marmitt.ctrade.domain.valueobject.Price;

import java.util.List;

public interface ExchangePort {
    Order placeOrder(Order order);
    Order cancelOrder(String orderId);
    Order getOrderStatus(String orderId);
    List<Order> getActiveOrders();
    Price getCurrentPrice(TradingPair tradingPair);
}