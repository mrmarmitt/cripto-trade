package com.marmitt.core.ports.outbound.order;

import com.marmitt.core.domain.Order;
import com.marmitt.core.domain.OrderStatus;
import com.marmitt.core.dto.order.OrderResult;

import java.util.concurrent.CompletableFuture;

public interface OrderExecutionPort {
    
    CompletableFuture<OrderResult> placeOrder(Order order);
    
    CompletableFuture<OrderResult> cancelOrder(String orderId);
    
    CompletableFuture<OrderResult> modifyOrder(String orderId, com.marmitt.core.dto.order.OrderModification modification);
    
    CompletableFuture<OrderStatus> getOrderStatus(String orderId);
    
    CompletableFuture<OrderStatus> getOrderStatusByClientId(String clientOrderId);
    
    void subscribeToOrderUpdates(OrderUpdateListener listener);
    
    void unsubscribeFromOrderUpdates();
    
    CompletableFuture<Void> connect();
    
    CompletableFuture<Void> disconnect();
    
    boolean isConnected();
    
    @FunctionalInterface
    interface OrderUpdateListener {
        void onOrderUpdate(OrderStatus orderStatus);
    }
}