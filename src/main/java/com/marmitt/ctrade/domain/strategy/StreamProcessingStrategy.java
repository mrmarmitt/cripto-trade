package com.marmitt.ctrade.domain.strategy;

import com.marmitt.ctrade.domain.dto.OrderUpdateMessage;
import com.marmitt.ctrade.domain.dto.PriceUpdateMessage;

import java.util.Optional;

/**
 * Estratégia para processamento de streams WebSocket de diferentes exchanges.
 * 
 * Implementa o padrão Strategy para permitir diferentes algoritmos de parsing
 * específicos para cada exchange (Binance, Kraken, etc.).
 */
public interface StreamProcessingStrategy {
    
    /**
     * Processa uma mensagem WebSocket raw e converte para PriceUpdateMessage.
     * 
     * @param rawMessage Mensagem raw recebida do WebSocket
     * @return Optional contendo PriceUpdateMessage se o parsing foi bem-sucedido
     */
    Optional<PriceUpdateMessage> processPriceUpdate(String rawMessage);
    
    /**
     * Processa uma mensagem WebSocket raw e converte para OrderUpdateMessage.
     * 
     * @param rawMessage Mensagem raw recebida do WebSocket
     * @return Optional contendo OrderUpdateMessage se o parsing foi bem-sucedido
     */
    Optional<OrderUpdateMessage> processOrderUpdate(String rawMessage);
    
    /**
     * Retorna o nome da exchange para qual esta estratégia é específica.
     * 
     * @return Nome da exchange (ex: "BINANCE", "KRAKEN")
     */
    String getExchangeName();
    
    /**
     * Verifica se esta estratégia pode processar o tipo de mensagem fornecido.
     * 
     * @param rawMessage Mensagem raw a ser verificada
     * @return true se pode processar, false caso contrário
     */
    boolean canProcess(String rawMessage);
}