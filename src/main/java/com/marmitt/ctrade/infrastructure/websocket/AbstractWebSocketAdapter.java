package com.marmitt.ctrade.infrastructure.websocket;

import com.marmitt.ctrade.domain.dto.OrderUpdateMessage;
import com.marmitt.ctrade.domain.dto.PriceUpdateMessage;
import com.marmitt.ctrade.domain.event.OrderUpdateEvent;
import com.marmitt.ctrade.domain.event.PriceUpdateEvent;
import com.marmitt.ctrade.domain.port.WebSocketPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Classe abstrata base para todos os adapters WebSocket.
 * 
 * Fornece:
 * - Publicação automática de eventos Spring para atualizações de preço/ordem
 * - Template methods para implementações específicas
 * - Eliminação de dependências circulares via event-driven architecture
 */
@Slf4j
public abstract class AbstractWebSocketAdapter implements WebSocketPort {
    
    @Autowired
    protected ApplicationEventPublisher eventPublisher;
    
    /**
     * Template method para processar atualizações de preço.
     * Implementações específicas devem sobrescrever este método.
     */
    protected void onPriceUpdate(PriceUpdateMessage priceUpdate) {
        log.debug("Publishing price update event for {}: {}", priceUpdate.getTradingPair(), priceUpdate.getPrice());
        
        PriceUpdateEvent event = PriceUpdateEvent.of(
            this, 
            priceUpdate, 
            getExchangeName()
        );
        
        eventPublisher.publishEvent(event);
    }
    
    /**
     * Template method para processar atualizações de ordem.
     * Implementações específicas devem sobrescrever este método.
     */
    protected void onOrderUpdate(OrderUpdateMessage orderUpdate) {
        log.debug("Publishing order update event for order {}: {}", orderUpdate.getOrderId(), orderUpdate.getStatus());
        
        OrderUpdateEvent event = OrderUpdateEvent.of(
            this, 
            orderUpdate, 
            getExchangeName()
        );
        
        eventPublisher.publishEvent(event);
    }
    
    /**
     * Retorna o nome da exchange para identificação nos eventos.
     * Deve ser implementado pelas classes filhas.
     */
    protected abstract String getExchangeName();
}