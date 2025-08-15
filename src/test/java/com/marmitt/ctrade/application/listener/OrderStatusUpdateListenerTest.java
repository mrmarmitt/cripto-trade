package com.marmitt.ctrade.application.listener;

import com.marmitt.ctrade.application.service.TradingService;
import com.marmitt.ctrade.domain.dto.OrderUpdateMessage;
import com.marmitt.ctrade.domain.entity.Order;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderStatusUpdateListenerTest {
    
    @Mock
    private TradingService tradingService;
    
    @InjectMocks
    private OrderStatusUpdateListener listener;
    
    private OrderUpdateMessage orderUpdate;
    
    @BeforeEach
    void setUp() {
        orderUpdate = new OrderUpdateMessage();
        orderUpdate.setOrderId("ORDER-123");
        orderUpdate.setStatus(Order.OrderStatus.FILLED);
        orderUpdate.setReason("Market execution completed");
        orderUpdate.setTimestamp(LocalDateTime.now());
    }
    
    @Test
    void shouldProcessOrderUpdateSuccessfully() {
        listener.onOrderUpdate(orderUpdate);
        
        verify(tradingService).processOrderStatusUpdate(
            "ORDER-123",
            Order.OrderStatus.FILLED,
            "Market execution completed"
        );
    }
    
    @Test
    void shouldHandleExceptionFromTradingService() {
        doThrow(new RuntimeException("Database error"))
            .when(tradingService)
            .processOrderStatusUpdate(anyString(), any(), anyString());
        
        // Não deve lançar exceção - apenas logar
        listener.onOrderUpdate(orderUpdate);
        
        verify(tradingService).processOrderStatusUpdate(
            "ORDER-123",
            Order.OrderStatus.FILLED,
            "Market execution completed"
        );
    }
}