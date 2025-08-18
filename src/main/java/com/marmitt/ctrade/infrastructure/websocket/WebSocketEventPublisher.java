package com.marmitt.ctrade.infrastructure.websocket;

import com.marmitt.ctrade.domain.dto.OrderUpdateMessage;
import com.marmitt.ctrade.domain.dto.PriceUpdateMessage;
import com.marmitt.ctrade.domain.event.OrderUpdateEvent;
import com.marmitt.ctrade.domain.event.PriceUpdateEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Serviço responsável pela publicação de eventos WebSocket.
 * 
 * Centraliza a lógica de criação e publicação de eventos de preço e ordem,
 * mantendo a separação entre infraestrutura e domínio.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventPublisher {
    
    private final ApplicationEventPublisher eventPublisher;
    
    /**
     * Publica evento de atualização de preço.
     */
    public void publishPriceUpdate(Object source, PriceUpdateMessage priceUpdate, String exchangeName) {
        if (priceUpdate != null) {
            log.debug("Publishing price update event for {}: {}", priceUpdate.getTradingPair(), priceUpdate.getPrice());
        } else {
            log.debug("Publishing null price update event from {}", exchangeName);
        }
        
        // ApplicationEvent não aceita source null, usar um placeholder
        Object eventSource = source != null ? source : "UNKNOWN_SOURCE";
        PriceUpdateEvent event = PriceUpdateEvent.of(eventSource, priceUpdate, exchangeName);
        eventPublisher.publishEvent(event);
    }
    
    /**
     * Publica evento de atualização de ordem.
     */
    public void publishOrderUpdate(Object source, OrderUpdateMessage orderUpdate, String exchangeName) {
        if (orderUpdate != null) {
            log.debug("Publishing order update event for order {}: {}", orderUpdate.getOrderId(), orderUpdate.getStatus());
        } else {
            log.debug("Publishing null order update event from {}", exchangeName);
        }
        
        // ApplicationEvent não aceita source null, usar um placeholder
        Object eventSource = source != null ? source : "UNKNOWN_SOURCE";
        OrderUpdateEvent event = OrderUpdateEvent.of(eventSource, orderUpdate, exchangeName);
        eventPublisher.publishEvent(event);
    }
}