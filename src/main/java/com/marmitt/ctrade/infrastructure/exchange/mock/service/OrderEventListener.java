package com.marmitt.ctrade.infrastructure.exchange.mock.service;

import com.marmitt.ctrade.domain.entity.Order;

/**
 * Interface para escutar eventos de mudança de status de ordens.
 * Permite conectar o MockExchangeAdapter ao sistema de WebSocket.
 */
public interface OrderEventListener {
    
    /**
     * Chamado quando uma ordem é criada ou atualizada.
     * 
     * @param order a ordem que foi modificada
     */
    void onOrderUpdated(Order order);
    
    /**
     * Chamado quando uma ordem é cancelada.
     * 
     * @param order a ordem cancelada
     */
    void onOrderCancelled(Order order);
    
    /**
     * Chamado quando uma ordem é preenchida (completa ou parcialmente).
     * 
     * @param order a ordem preenchida
     */
    void onOrderFilled(Order order);
}