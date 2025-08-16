package com.marmitt.ctrade.application.service;

import com.marmitt.ctrade.domain.dto.OrderUpdateMessage;
import com.marmitt.ctrade.domain.dto.PriceUpdateMessage;
import com.marmitt.ctrade.domain.event.OrderUpdateEvent;
import com.marmitt.ctrade.domain.event.PriceUpdateEvent;
import com.marmitt.ctrade.domain.listener.OrderUpdateListener;
import com.marmitt.ctrade.domain.listener.PriceUpdateListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketHandler {

    private final List<PriceUpdateListener> priceUpdateListeners;
    private final List<OrderUpdateListener> orderUpdateListeners;

    /**
     * Event listener para atualizações de preço.
     * Elimina a dependência circular permitindo que adapters publiquem eventos.
     */
    @EventListener
    public void handlePriceUpdateEvent(PriceUpdateEvent event) {
        PriceUpdateMessage message = event.getPriceUpdate();
        log.info("Price update event received from {}: {} = {}",
                event.getSource(), message.getTradingPair(), message.getPrice());

        priceUpdateListeners.forEach(listener -> {
            try {
                listener.onPriceUpdate(message);
            } catch (Exception e) {
                log.error("Error processing price update in listener {}: {}",
                        listener.getClass().getSimpleName(), e.getMessage(), e);
            }
        });
    }

    /**
     * Event listener para atualizações de ordem.
     * Elimina a dependência circular permitindo que adapters publiquem eventos.
     */
    @EventListener
    public void handleOrderUpdateEvent(OrderUpdateEvent event) {
        OrderUpdateMessage message = event.getOrderUpdate();
        log.info("Order update event received from {}: {} status: {}",
                event.getSource(), message.getOrderId(), message.getStatus());

        orderUpdateListeners.forEach(listener -> {
            try {
                listener.onOrderUpdate(message);
            } catch (Exception e) {
                log.error("Error processing order update in listener {}: {}",
                        listener.getClass().getSimpleName(), e.getMessage(), e);
            }
        });
    }
}
