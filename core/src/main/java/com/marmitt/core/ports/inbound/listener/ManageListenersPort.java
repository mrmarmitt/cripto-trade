package com.marmitt.core.ports.inbound.listener;

import com.marmitt.core.dto.listener.ListenerStats;
import com.marmitt.core.ports.outbound.listener.OrderUpdateListener;
import com.marmitt.core.ports.outbound.listener.PriceUpdateListener;

import java.util.List;

/**
 * Port inbound para gerenciamento de listeners.
 * Define operações de registro, remoção e consulta de listeners iniciadas externamente.
 */
public interface ManageListenersPort {
    
    /**
     * Registra um listener para atualizações de ordens.
     * 
     * @param listener listener a ser registrado
     * @return true se o listener foi registrado com sucesso, false se já estava registrado
     */
    boolean registerOrderUpdateListener(OrderUpdateListener listener);
    
    /**
     * Remove o registro de um listener de atualizações de ordens.
     * 
     * @param listener listener a ser removido
     * @return true se o listener foi removido com sucesso, false se não foi encontrado
     */
    boolean unregisterOrderUpdateListener(OrderUpdateListener listener);
    
    /**
     * Registra um listener para atualizações de preços.
     * 
     * @param listener listener a ser registrado
     * @return true se o listener foi registrado com sucesso, false se já estava registrado
     */
    boolean registerPriceUpdateListener(PriceUpdateListener listener);
    
    /**
     * Remove o registro de um listener de atualizações de preços.
     * 
     * @param listener listener a ser removido
     * @return true se o listener foi removido com sucesso, false se não foi encontrado
     */
    boolean unregisterPriceUpdateListener(PriceUpdateListener listener);
    
    /**
     * Lista todos os listeners de atualizações de ordens registrados.
     * 
     * @return lista imutável de listeners registrados
     */
    List<OrderUpdateListener> getAllOrderUpdateListeners();
    
    /**
     * Lista todos os listeners de atualizações de preços registrados.
     * 
     * @return lista imutável de listeners registrados
     */
    List<PriceUpdateListener> getAllPriceUpdateListeners();
    
    /**
     * Retorna estatísticas dos listeners registrados.
     * 
     * @return informações sobre quantos listeners estão registrados
     */
    ListenerStats getListenerStats();
    
    /**
     * Limpa todos os listeners registrados.
     */
    void clearAllListeners();
}