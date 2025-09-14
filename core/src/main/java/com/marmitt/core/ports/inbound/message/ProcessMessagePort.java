package com.marmitt.core.ports.inbound.message;

import com.marmitt.core.dto.processing.ProcessingResult;
import com.marmitt.core.dto.websocket.MessageContext;

/**
 * Port inbound para processamento de mensagens.
 * Define operações para processar mensagens recebidas de exchanges.
 */
public interface ProcessMessagePort {
    
    /**
     * Processa uma mensagem crua recebida de uma exchange.
     * 
     * @param rawMessage mensagem crua recebida
     * @param context contexto da mensagem com informações de correlação
     * @return resultado do processamento com dados ou erro
     */
    ProcessingResult<?> processRawMessage(String rawMessage, MessageContext context);
}