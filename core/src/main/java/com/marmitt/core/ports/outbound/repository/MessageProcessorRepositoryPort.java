package com.marmitt.core.ports.outbound.repository;

import com.marmitt.core.ports.outbound.websocket.AdapterMessageProcessorPort;

import java.util.Optional;

/**
 * Repository port para gerenciar implementações de MessageProcessor por exchange.
 * Define operações para registrar e recuperar processors específicos de cada exchange.
 */
public interface MessageProcessorRepositoryPort {
    
    /**
     * Registra um MessageProcessor para uma exchange específica.
     * 
     * @param exchangeName nome da exchange (ex: "BINANCE", "COINBASE")
     * @param processor implementação do processor para a exchange
     */
    void registerProcessor(String exchangeName, AdapterMessageProcessorPort processor);
    
    /**
     * Recupera o MessageProcessor registrado para uma exchange.
     * 
     * @param exchangeName nome da exchange
     * @return processor da exchange ou Optional.empty() se não encontrado
     */
    Optional<AdapterMessageProcessorPort> getProcessor(String exchangeName);
    
    /**
     * Verifica se existe um processor registrado para a exchange.
     * 
     * @param exchangeName nome da exchange
     * @return true se existe um processor registrado
     */
    boolean hasProcessor(String exchangeName);
    
    /**
     * Remove o processor registrado para uma exchange.
     * 
     * @param exchangeName nome da exchange
     * @return true se o processor foi removido, false se não foi encontrado
     */
    boolean removeProcessor(String exchangeName);
    
    /**
     * Retorna o número total de processors registrados.
     * 
     * @return contagem de processors
     */
    int getProcessorCount();
}