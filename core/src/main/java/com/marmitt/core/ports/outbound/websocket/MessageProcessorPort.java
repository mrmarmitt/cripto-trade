package com.marmitt.core.ports.outbound.websocket;

import com.marmitt.core.domain.data.ProcessorResponse;
import com.marmitt.core.dto.processing.ProcessingResult;
import com.marmitt.core.dto.websocket.MessageContext;

public interface MessageProcessorPort {
    
    /**
     * Processa uma mensagem WebSocket raw e retorna um resultado tipado
     * 
     * @param rawMessage mensagem JSON recebida do WebSocket
     * @param context contexto da mensagem contendo correlationId, exchange, etc.
     * @return ProcessingResult com ProcessorResponse (MarketData, OrderData, etc.) ou erro
     */
    ProcessingResult<? extends ProcessorResponse> processMessage(String rawMessage, MessageContext context);

}