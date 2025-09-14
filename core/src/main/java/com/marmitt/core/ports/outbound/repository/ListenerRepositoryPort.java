package com.marmitt.core.ports.outbound.repository;

import com.marmitt.core.ports.outbound.listener.OrderUpdateListener;
import com.marmitt.core.ports.outbound.listener.PriceUpdateListener;

import java.util.List;

/**
 * Repository port para gerenciar listeners registrados em memória.
 * Define operações de persistência e recuperação de listeners.
 */
public interface ListenerRepositoryPort {
    
    /**
     * Adiciona um OrderUpdateListener ao repositório.
     * 
     * @param listener listener a ser adicionado
     * @return true se o listener foi adicionado com sucesso, false se já existia
     */
    boolean addOrderUpdateListener(OrderUpdateListener listener);
    
    /**
     * Remove um OrderUpdateListener do repositório.
     * 
     * @param listener listener a ser removido
     * @return true se o listener foi removido com sucesso, false se não foi encontrado
     */
    boolean removeOrderUpdateListener(OrderUpdateListener listener);
    
    /**
     * Recupera todos os OrderUpdateListeners registrados.
     * 
     * @return lista imutável de listeners registrados
     */
    List<OrderUpdateListener> getAllOrderUpdateListeners();
    
    /**
     * Adiciona um PriceUpdateListener ao repositório.
     * 
     * @param listener listener a ser adicionado
     * @return true se o listener foi adicionado com sucesso, false se já existia
     */
    boolean addPriceUpdateListener(PriceUpdateListener listener);
    
    /**
     * Remove um PriceUpdateListener do repositório.
     * 
     * @param listener listener a ser removido
     * @return true se o listener foi removido com sucesso, false se não foi encontrado
     */
    boolean removePriceUpdateListener(PriceUpdateListener listener);
    
    /**
     * Recupera todos os PriceUpdateListeners registrados.
     * 
     * @return lista imutável de listeners registrados
     */
    List<PriceUpdateListener> getAllPriceUpdateListeners();
    
    /**
     * Retorna o número total de OrderUpdateListeners registrados.
     * 
     * @return contagem de listeners
     */
    int getOrderUpdateListenerCount();
    
    /**
     * Retorna o número total de PriceUpdateListeners registrados.
     * 
     * @return contagem de listeners
     */
    int getPriceUpdateListenerCount();
    
    /**
     * Limpa todos os listeners registrados.
     */
    void clearAllListeners();
}