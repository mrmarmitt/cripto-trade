package com.marmitt.ctrade.infrastructure.exchange.mock.service;

import com.marmitt.ctrade.domain.dto.OrderUpdateMessage;
import com.marmitt.ctrade.domain.entity.Order;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

/**
 * Conversor que transforma Orders em OrderUpdateMessage para integração com WebSocket.
 */
@Slf4j
public class OrderUpdateConverter {
    
    /**
     * Converte uma Order em OrderUpdateMessage para ser processada pelo sistema WebSocket.
     */
    public static OrderUpdateMessage convertToOrderUpdate(Order order) {
        OrderUpdateMessage message = new OrderUpdateMessage();
        
        message.setOrderId(order.getId());
        message.setStatus(order.getStatus()); // Usa diretamente o enum
        message.setTimestamp(LocalDateTime.now());
        
        // Adiciona informações contextuais no reason field
        String reason = String.format("Order %s %s %s %s at %s", 
            order.getSide().name(), 
            order.getQuantity(), 
            order.getTradingPair().getSymbol(),
            order.getType().name(),
            order.getPrice());
        message.setReason(reason);
        
        log.debug("Converted order {} to OrderUpdateMessage: status={}", 
            order.getId(), message.getStatus());
        
        return message;
    }
}