package com.marmitt.core.ports.outbound.events;

/**
 * Outbound port para publicação de eventos no sistema.
 * 
 * Abstrai a implementação específica do mecanismo de eventos
 * (Spring Events, message broker, etc.) permitindo que os use cases
 * publiquem eventos sem depender de frameworks específicos.
 */
public interface EventPublisherPort {
    
    /**
     * Publica um evento no sistema para processamento assíncrono.
     * 
     * @param event Evento a ser publicado
     */
    void publishEvent(Object event);
    
    /**
     * Publica um evento de forma síncrona, aguardando o processamento.
     * 
     * @param event Evento a ser publicado
     */
    void publishEventSync(Object event);
}