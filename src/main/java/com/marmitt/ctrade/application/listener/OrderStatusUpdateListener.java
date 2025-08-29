package com.marmitt.ctrade.application.listener;

import com.marmitt.ctrade.application.service.ExchangeTradingService;
import com.marmitt.ctrade.domain.dto.OrderUpdateMessage;
import com.marmitt.ctrade.domain.listener.OrderUpdateListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderStatusUpdateListener implements OrderUpdateListener {
    
    private final ExchangeTradingService exchangeTradingService;
    
    @Override
    public void onOrderUpdate(OrderUpdateMessage orderUpdate) {
        log.info("Received order update via WebSocket: orderId={}, status={}", 
                orderUpdate.getOrderId(), orderUpdate.getStatus());
        
        try {
            exchangeTradingService.processOrderStatusUpdate(
                orderUpdate.getOrderId(),
                orderUpdate.getStatus(),
                orderUpdate.getReason()
            );
            
            log.debug("Successfully processed order update for: {}", orderUpdate.getOrderId());
            
        } catch (Exception e) {
            log.error("Failed to process order update for orderId {}: {}", 
                     orderUpdate.getOrderId(), e.getMessage(), e);
        }
    }
}