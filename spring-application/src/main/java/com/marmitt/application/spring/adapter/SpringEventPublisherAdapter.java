package com.marmitt.application.spring.adapter;

import com.marmitt.core.ports.outbound.events.EventPublisherPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Implementação do EventPublisherPort usando Spring Events.
 * 
 * Adapter que permite ao core publicar eventos sem conhecer
 * a implementação específica do Spring Framework.
 */
@Component
public class SpringEventPublisherAdapter implements EventPublisherPort {

    private final ApplicationEventPublisher applicationEventPublisher;

    public SpringEventPublisherAdapter(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void publishEvent(Object event) {
        applicationEventPublisher.publishEvent(event);
    }

    @Override
    public void publishEventSync(Object event) {
        // Spring Events são síncronos por padrão
        // Para assíncrono seria necessário @Async nos listeners
        applicationEventPublisher.publishEvent(event);
    }
}