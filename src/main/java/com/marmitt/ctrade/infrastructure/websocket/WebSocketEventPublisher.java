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
        log.debug("Publishing price update event for {}: {}", priceUpdate.getTradingPair(), priceUpdate.getPrice());
        
        PriceUpdateEvent event = PriceUpdateEvent.of(source, priceUpdate, exchangeName);
        eventPublisher.publishEvent(event);
    }
    
    /**
     * Publica evento de atualização de ordem.
     */
    public void publishOrderUpdate(Object source, OrderUpdateMessage orderUpdate, String exchangeName) {
        log.debug("Publishing order update event for order {}: {}", orderUpdate.getOrderId(), orderUpdate.getStatus());
        
        OrderUpdateEvent event = OrderUpdateEvent.of(source, orderUpdate, exchangeName);
        eventPublisher.publishEvent(event);
    }
}