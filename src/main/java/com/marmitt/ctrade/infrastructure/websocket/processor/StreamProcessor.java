package com.marmitt.ctrade.infrastructure.websocket.processor;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Optional;

/**
 * Strategy interface para processar diferentes tipos de streams da Binance.
 * Cada implementação é responsável por processar um tipo específico de stream
 * (ticker, bookTicker, userData, etc.).
 * 
 * Usa callbacks ao invés de dependência direta em WebSocketService para eliminar acoplamento.
 */
public interface StreamProcessor<T> {
    
    /**
     * Verifica se este processor pode processar o stream especificado.
     * 
     * @param streamName Nome do stream (ex: "!ticker@arr", "!bookTicker@arr")
     * @return true se pode processar, false caso contrário
     */
    boolean canProcess(String streamName);
    
    /**
     * Processa os dados do stream específico usando callbacks.
     * 
     * @param data Dados do stream (formato específico de cada stream)
     * @return PriceUpdateMessage
     */
    Optional<T> process(JsonNode data);
    
    /**
     * Retorna o nome do stream que este processor manipula.
     * Usado para identificação e logs.
     * 
     * @return Nome do stream
     */
    String getStreamName();
}