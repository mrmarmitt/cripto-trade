package com.marmitt.cripto_trade.domain.port;

import com.marmitt.cripto_trade.domain.entity.Order;
import com.marmitt.cripto_trade.domain.entity.TradingPair;
import com.marmitt.cripto_trade.domain.valueobject.Price;

import java.util.List;

public interface ExchangePort {
    Order placeOrder(Order order);
    Order cancelOrder(String orderId);
    Order getOrderStatus(String orderId);
    List<Order> getActiveOrders();
    Price getCurrentPrice(TradingPair tradingPair);
}