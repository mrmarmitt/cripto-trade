package com.marmitt.ctrade.infrastructure.exchange.binance.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.ctrade.domain.dto.OrderUpdateMessage;
import com.marmitt.ctrade.domain.dto.PriceUpdateMessage;
import com.marmitt.ctrade.domain.strategy.StreamProcessingStrategy;
import com.marmitt.ctrade.infrastructure.exchange.binance.dto.StreamWrapper;
import com.marmitt.ctrade.infrastructure.exchange.binance.strategy.processor.TickerStreamProcessor;
import com.marmitt.ctrade.domain.strategy.processor.StreamProcessor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Estratégia de processamento de streams WebSocket específica para Binance.
 * 
 * Implementa o padrão Strategy para processar mensagens WebSocket da Binance,
 * suportando tanto streams únicos quanto multiplexados.
 */
@Slf4j
public class BinanceStreamProcessingStrategy implements StreamProcessingStrategy {
    
    private final ObjectMapper objectMapper;
    private final List<StreamProcessor<PriceUpdateMessage>> priceProcessors;
    private final List<StreamProcessor<OrderUpdateMessage>> orderProcessors;

    public BinanceStreamProcessingStrategy(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        
        // Inicializa processors específicos para price updates
        this.priceProcessors = new ArrayList<>();
        this.priceProcessors.add(new TickerStreamProcessor(objectMapper));
        // this.priceProcessors.add(new BookTickerStreamProcessor(objectMapper));
        
        // Inicializa processors específicos para order updates
        this.orderProcessors = new ArrayList<>();
        // this.orderProcessors.add(new UserDataStreamProcessor(objectMapper));
        // this.orderProcessors.add(new ExecutionReportProcessor(objectMapper));
    }

    @Override
    public Optional<PriceUpdateMessage> processPriceUpdate(String rawMessage) {
        try {
            log.debug("Processing Binance price update: {}", rawMessage.substring(0, Math.min(100, rawMessage.length())));
            
            if (isMultiplexedStream(rawMessage)) {
                return parseMultiplexedPriceStream(rawMessage);
            } else {
                return parseSinglePriceStream(rawMessage);
            }
            
        } catch (Exception e) {
            log.error("Error parsing Binance price update message: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }
    
    @Override
    public Optional<OrderUpdateMessage> processOrderUpdate(String rawMessage) {
        try {
            log.debug("Processing Binance order update: {}", rawMessage.substring(0, Math.min(100, rawMessage.length())));
            
            if (isMultiplexedStream(rawMessage)) {
                return parseMultiplexedOrderStream(rawMessage);
            } else {
                return parseSingleOrderStream(rawMessage);
            }
            
        } catch (Exception e) {
            log.error("Error parsing Binance order update message: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }
    
    @Override
    public String getExchangeName() {
        return "BINANCE";
    }
    
    @Override
    public boolean canProcess(String rawMessage) {
        try {
            // Verifica se é um JSON válido com estrutura típica da Binance
            JsonNode node = objectMapper.readTree(rawMessage);
            
            // Stream multiplexado da Binance
            if (node.has("stream") && node.has("data")) {
                return true;
            }
            
            // Stream único da Binance (array de tickers ou outros)
            if (node.isArray() || node.has("s") || node.has("c")) {
                return true;
            }
            
            return false;
        } catch (Exception e) {
            log.debug("Cannot process message as Binance format: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Verifica se a mensagem é de um stream multiplexado.
     * Streams multiplexados têm formato: {"stream": "...", "data": ...}
     */
    private boolean isMultiplexedStream(String jsonMessage) {
        return jsonMessage.contains("\"stream\"") && jsonMessage.contains("\"data\"");
    }
    
    /**
     * Processa streams multiplexados para price updates.
     */
    private Optional<PriceUpdateMessage> parseMultiplexedPriceStream(String jsonMessage) throws Exception {
        StreamWrapper wrapper = objectMapper.readValue(jsonMessage, StreamWrapper.class);
        
        log.debug("Processing multiplexed price stream: {}", wrapper.getStream());
        
        StreamProcessor<PriceUpdateMessage> processor = findPriceProcessor(wrapper.getStream());
        if (processor != null) {
            return processor.process(wrapper.getData());
        } else {
            log.warn("No price processor found for stream: {}", wrapper.getStream());
            return Optional.empty();
        }
    }
    
    /**
     * Processa streams únicos para price updates.
     */
    private Optional<PriceUpdateMessage> parseSinglePriceStream(String jsonMessage) throws Exception {
        JsonNode wrapper = objectMapper.readValue(jsonMessage, JsonNode.class);
        log.debug("Processing single stream ticker array messages");
        
        StreamProcessor<PriceUpdateMessage> tickerProcessor = findPriceProcessor("!ticker@arr");
        if (tickerProcessor != null) {
            return tickerProcessor.process(wrapper);
        } else {
            log.warn("No ticker processor found for single stream processing");
            return Optional.empty();
        }
    }
    
    /**
     * Processa streams multiplexados para order updates.
     */
    private Optional<OrderUpdateMessage> parseMultiplexedOrderStream(String jsonMessage) throws Exception {
        StreamWrapper wrapper = objectMapper.readValue(jsonMessage, StreamWrapper.class);
        
        log.debug("Processing multiplexed order stream: {}", wrapper.getStream());
        
        // Streams de ticker não contêm dados de ordem
        if (wrapper.getStream().contains("ticker")) {
            log.debug("Stream {} contains price data, not order data. Returning empty.", wrapper.getStream());
            return Optional.empty();
        }
        
        StreamProcessor<OrderUpdateMessage> processor = findOrderProcessor(wrapper.getStream());
        if (processor != null) {
            return processor.process(wrapper.getData());
        } else {
            log.debug("No order processor found for stream: {}", wrapper.getStream());
            return Optional.empty();
        }
    }
    
    /**
     * Processa streams únicos para order updates.
     */
    private Optional<OrderUpdateMessage> parseSingleOrderStream(String jsonMessage) throws Exception {
        JsonNode wrapper = objectMapper.readValue(jsonMessage, JsonNode.class);
        log.debug("Processing single stream order messages");
        
        // Para streams únicos, assumimos que são ticker arrays se não especificado
        // Ticker arrays não contêm dados de ordem
        log.debug("Single stream processing is typically ticker data, not order data. Returning empty.");
        return Optional.empty();
    }
    
    /**
     * Encontra o processor apropriado para price updates.
     */
    private StreamProcessor<PriceUpdateMessage> findPriceProcessor(String streamName) {
        return priceProcessors.stream()
                .filter(processor -> processor.canProcess(streamName))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * Encontra o processor apropriado para order updates.
     */
    private StreamProcessor<OrderUpdateMessage> findOrderProcessor(String streamName) {
        return orderProcessors.stream()
                .filter(processor -> processor.canProcess(streamName))
                .findFirst()
                .orElse(null);
    }
}