package com.marmitt.ctrade.domain.event;

import com.marmitt.ctrade.domain.dto.PriceUpdateMessage;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Evento publicado quando uma atualização de preço é recebida de qualquer exchange.
 * Permite desacoplar os adapters WebSocket dos serviços que processam atualizações de preço.
 */
@Getter
public class PriceUpdateEvent extends ApplicationEvent {
    
    private final PriceUpdateMessage priceUpdate;
    private final String source; // Nome da exchange ou adapter (ex: "BINANCE", "MOCK")
    
    public PriceUpdateEvent(Object source, PriceUpdateMessage priceUpdate, String exchangeSource) {
        super(source);
        this.priceUpdate = priceUpdate;
        this.source = exchangeSource;
    }
    
    /**
     * Método conveniente para criar eventos com source automático.
     */
    public static PriceUpdateEvent of(Object source, PriceUpdateMessage priceUpdate, String exchangeSource) {
        return new PriceUpdateEvent(source, priceUpdate, exchangeSource);
    }
}