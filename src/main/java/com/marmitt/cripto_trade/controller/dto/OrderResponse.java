package com.marmitt.cripto_trade.controller.dto;

import com.marmitt.cripto_trade.domain.entity.Order;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {
    private String id;
    private String tradingPair;
    private String type;
    private String side;
    private BigDecimal quantity;
    private BigDecimal price;
    private String status;
    private BigDecimal totalValue;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static OrderResponse fromOrder(Order order) {
        return new OrderResponse(
            order.getId(),
            order.getTradingPair().getSymbol(),
            order.getType().toString(),
            order.getSide().toString(),
            order.getQuantity(),
            order.getPrice(),
            order.getStatus().toString(),
            order.getTotalValue(),
            order.getCreatedAt(),
            order.getUpdatedAt()
        );
    }
}