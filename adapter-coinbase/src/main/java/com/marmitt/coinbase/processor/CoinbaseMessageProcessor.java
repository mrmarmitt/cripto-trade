package com.marmitt.coinbase.processor;

import com.marmitt.core.domain.data.ProcessorResponse;
import com.marmitt.core.dto.processing.ProcessingResult;
import com.marmitt.core.dto.websocket.MessageContext;
import com.marmitt.core.ports.outbound.websocket.MessageProcessorPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.coinbase.request.TickerStreamRequest;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.List;

@Slf4j
public class CoinbaseMessageProcessor implements MessageProcessorPort {
    
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public ProcessingResult<? extends ProcessorResponse> processMessage(String rawMessage, MessageContext context) {
        log.info("Coinbase - Processing message: correlationId={}, length={}", 
                context.correlationId(), rawMessage.length());
        
        // TODO: Implementar processamento real quando necessário
        ProcessingResult<Object> success = ProcessingResult.success(context.correlationId().toString(), rawMessage);
        return castToProcessorResponseResult(success);
    }

    public String createSubscribeMessage(String symbols) {
        try {
            // Converte string "BTC-USD,ETH-USD" para lista
            List<String> productIds = Arrays.asList(symbols.split(","));
            
            TickerStreamRequest request = TickerStreamRequest.subscribe(productIds);
            return objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            System.err.println("Error creating subscribe message: " + e.getMessage());
            return "{\"type\":\"subscribe\",\"product_ids\":[\"BTC-USD\"],\"channels\":[\"ticker\"]}";
        }
    }
    
    @SuppressWarnings({"unchecked", "rawtypes"})
    private ProcessingResult<? extends ProcessorResponse> castToProcessorResponseResult(ProcessingResult<Object> result) {
        return (ProcessingResult) result;
    }
}