package com.marmitt.ctrade.domain.dto;

import com.marmitt.ctrade.domain.entity.Order;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class OrderUpdateMessage {
    
    private String orderId;
    private Order.OrderStatus status;
    private String reason;
    private LocalDateTime timestamp;
}