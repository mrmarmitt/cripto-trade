package com.marmitt.listener;

import com.marmitt.core.domain.data.OrderData;
import com.marmitt.core.ports.outbound.listener.OrderUpdateListener;
import lombok.extern.slf4j.Slf4j;


/**
 * Implementação exemplo de OrderUpdateListener para logging e processamento de ordens.
 * Esta implementação demonstra como receber e processar atualizações de ordens.
 */
@Slf4j
public class TradingOrderUpdateListener implements OrderUpdateListener {
    

    @Override
    public void onOrderUpdate(OrderData orderData) {
        log.info("Order Update Received - OrderId: {}, Symbol: {}, Status: {}, Quantity: {}, Price: {}", 
                orderData.orderId(),
                orderData.symbol(),
                orderData.status(),
                orderData.quantity(),
                orderData.price());
        
        // Aqui você pode implementar lógica específica baseada no status da ordem
        switch (orderData.status()) {
            case FILLED:
                log.info("Order {} was completely filled", orderData.orderId());
                // Implementar lógica para ordem executada
                break;
            case PARTIALLY_FILLED:
                log.info("Order {} was partially filled", orderData.orderId());
                // Implementar lógica para execução parcial
                break;
            case CANCELED:
                log.info("Order {} was cancelled", orderData.orderId());
                // Implementar lógica para ordem cancelada
                break;
            case REJECTED:
                log.warn("Order {} was rejected", orderData.orderId());
                // Implementar lógica para ordem rejeitada
                break;
            default:
                log.debug("Order {} status updated to {}", orderData.orderId(), orderData.status());
        }
    }
}