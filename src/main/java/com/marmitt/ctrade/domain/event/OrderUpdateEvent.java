package com.marmitt.ctrade.domain.event;

import com.marmitt.ctrade.domain.dto.OrderUpdateMessage;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Evento publicado quando uma atualização de ordem é recebida de qualquer exchange.
 * Permite desacoplar os adapters WebSocket dos serviços que processam atualizações de ordem.
 */
@Getter
public class OrderUpdateEvent extends ApplicationEvent {
    
    private final OrderUpdateMessage orderUpdate;
    private final String source; // Nome da exchange ou adapter (ex: "BINANCE", "MOCK")
    
    public OrderUpdateEvent(Object source, OrderUpdateMessage orderUpdate, String exchangeSource) {
        super(source);
        this.orderUpdate = orderUpdate;
        this.source = exchangeSource;
    }
    
    /**
     * Método conveniente para criar eventos com source automático.
     */
    public static OrderUpdateEvent of(Object source, OrderUpdateMessage orderUpdate, String exchangeSource) {
        return new OrderUpdateEvent(source, orderUpdate, exchangeSource);
    }
}