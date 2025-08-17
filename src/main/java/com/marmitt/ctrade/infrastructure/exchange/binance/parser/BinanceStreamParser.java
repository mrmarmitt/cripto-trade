package com.marmitt.ctrade.infrastructure.exchange.binance.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.ctrade.domain.dto.PriceUpdateMessage;
import com.marmitt.ctrade.infrastructure.exchange.binance.dto.StreamWrapper;
import com.marmitt.ctrade.infrastructure.websocket.processor.StreamProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Parser principal responsável por processar mensagens WebSocket da Binance.
 * Suporta tanto streams únicos (!ticker@arr) quanto streams multiplexados
 * (/stream?streams=!ticker@arr/!bookTicker@arr).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BinanceStreamParser {
    
    private final ObjectMapper objectMapper;
    private final List<StreamProcessor> processors;
    
    /**
     * Processa uma mensagem WebSocket da Binance usando callbacks.
     * Detecta automaticamente se é formato simples ou multiplexado.
     * 
     * @param jsonMessage Mensagem JSON recebida do WebSocket
     */
    public Optional<PriceUpdateMessage> parseMessage(String jsonMessage) {
        try {
            log.debug("Processing Binance message: {}", jsonMessage.substring(0, Math.min(100, jsonMessage.length())));
            
            if (isMultiplexedStream(jsonMessage)) {
                return parseMultiplexedStream(jsonMessage);
            } else {
               return parseSingleStream(jsonMessage);
            }
            
        } catch (Exception e) {
            log.error("Error parsing Binance WebSocket message: {}", e.getMessage(), e);
        }

        return Optional.empty();
    }
    
    /**
     * Verifica se a mensagem é de um stream multiplexado.
     * Streams multiplexados têm formato: {"stream": "...", "data": ...}
     */
    private boolean isMultiplexedStream(String jsonMessage) {
        return jsonMessage.contains("\"stream\"") && jsonMessage.contains("\"data\"");
    }
    
    /**
     * Processa streams multiplexados (formato wrapper).
     */
    private Optional<PriceUpdateMessage> parseMultiplexedStream(String jsonMessage) throws Exception {
        StreamWrapper wrapper = objectMapper.readValue(jsonMessage, StreamWrapper.class);
        
        log.debug("Processing multiplexed stream: {}", wrapper.getStream());
        
        StreamProcessor<PriceUpdateMessage> processor = findProcessor(wrapper.getStream());
        if (processor != null) {
            return processor.process(wrapper.getData());
        } else {
            log.warn("No processor found for stream: {}", wrapper.getStream());
            return Optional.empty();
        }
    }
    
    /**
     * Processa streams únicos (formato direto, compatibilidade com implementação atual).
     */
    private Optional<PriceUpdateMessage> parseSingleStream(String jsonMessage) throws Exception {
        JsonNode wrapper = objectMapper.readValue(jsonMessage, JsonNode.class);
        log.debug("Processing single stream ticker array messages");
        
        StreamProcessor<PriceUpdateMessage> tickerProcessor = findProcessor("!ticker@arr");
        if (tickerProcessor != null) {
            return tickerProcessor.process(wrapper);
        } else {
            log.warn("No ticker processor found for single stream processing");
            return Optional.empty();
        }
    }
    
    /**
     * Encontra o processor apropriado para o stream especificado.
     */
    private StreamProcessor findProcessor(String streamName) {
        return processors.stream()
                .filter(processor -> processor.canProcess(streamName))
                .findFirst()
                .orElse(null);
    }
}