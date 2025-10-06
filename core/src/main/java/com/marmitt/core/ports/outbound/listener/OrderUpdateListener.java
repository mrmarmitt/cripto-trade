package com.marmitt.core.ports.outbound.listener;

import com.marmitt.core.domain.data.OrderData;

/**
 * Interface para receber notificações de atualizações de ordens.
 * Implementações desta interface serão notificadas quando houver
 * mudanças no status de ordens (execução, cancelamento, etc.).
 */
public interface OrderUpdateListener {
    
    /**
     * Método chamado quando uma ordem é atualizada.
     * 
     * @param orderData dados atualizados da ordem
     */
    void onOrderUpdate(OrderData orderData);
}