package com.marmitt.core.application.listener.portfolio;

import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.ports.outbound.listener.OrderUpdateListener;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class PortfolioOrderUpdateListener implements OrderUpdateListener {

    private final StrategyExecution strategyExecution;

    public PortfolioOrderUpdateListener(StrategyExecution strategyExecution) {
        this.strategyExecution = strategyExecution;
    }

    public void onOrderUpdate(OrderDataDto orderData) {
        log.debug("Order update notification received - Order: {}, Status: {}",
                orderData.orderId(), orderData.status());

        // TODO: RECONCILIAÇÃO COM ORDEM PENDENTE
        // String clientOrderId = orderResult.getClientOrderId(); // Vem da resposta da exchange
        // OrderPendingEntity pendingOrder = orderRepository.findByClientOrderId(clientOrderId);
        // if (pendingOrder == null) {
        //     log.warn("Received order update for unknown clientOrderId: {}", clientOrderId);
        //     return;
        // }

        // TODO: ATUALIZAR STATUS DA ORDEM PERSISTIDA
        // orderRepository.updateOrderStatus(clientOrderId, orderResult.status(), Instant.now());

        // Roteamento baseado no status
        switch (orderData.status()) {
            case OrderDataDto.OrderStatus.FILLED, OrderDataDto.OrderStatus.PARTIALLY_FILLED -> onOrderExecuted(orderData);
            case OrderDataDto.OrderStatus.CANCELED -> onOrderCancelled(orderData);
            case EXPIRED -> onOrderExpired(orderData);
            default -> {
                log.debug("Order update for non-final status - Order: {}, Status: {}",
                        orderData.orderId(), orderData.status());
                // Estados SUBMITTED, ACCEPTED não requerem ação especial

                // TODO: ATUALIZAR ORDEM PENDENTE COM STATUS INTERMEDIÁRIO
                // orderRepository.updateOrderStatus(clientOrderId, orderResult.status(), Instant.now());
            }
        }
    }

    private void onOrderExecuted(OrderDataDto orderData) {
        log.debug("Order executed notification received - Order: {}, Status: {}", 
                 orderData.orderId(), orderData.status());
        
        try {
//            strategyExecution.handleAsyncOrderExecution(orderData);
        } catch (Exception e) {
            log.error("Error handling order execution notification - Order: {}, Error: {}", 
                     orderData.orderId(), e.getMessage(), e);
        }
    }

    private void onOrderCancelled(OrderDataDto orderData) {
        log.info("Order cancelled notification received - Order: {}",
                orderData.orderId());
        
        try {
//            strategyExecution.handleAsyncOrderExecution(orderData);
        } catch (Exception e) {
            log.error("Error handling order cancellation notification - Order: {}, Error: {}",
                    orderData.orderId(), e.getMessage(), e);
        }
    }

    private void onOrderExpired(OrderDataDto orderData) {
        log.warn("Order expired notification received - Order: {}",
                orderData.orderId());
        
        try {
//            strategyExecution.handleAsyncOrderExecution(orderData);
        } catch (Exception e) {
            log.error("Error handling order expiration notification - Order: {}, Error: {}",
                    orderData.orderId(), e.getMessage(), e);
        }
    }


    @Override
    public boolean shouldProcess(OrderDataDto orderData) {
        return orderData.status() == OrderDataDto.OrderStatus.FILLED ||
                orderData.status() == OrderDataDto.OrderStatus.PARTIALLY_FILLED ||
                orderData.status() == OrderDataDto.OrderStatus.CANCELED ||
                orderData.status() == OrderDataDto.OrderStatus.EXPIRED;
    }
}