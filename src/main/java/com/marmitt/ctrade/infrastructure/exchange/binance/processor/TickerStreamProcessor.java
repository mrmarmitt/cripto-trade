package com.marmitt.ctrade.infrastructure.exchange.binance.processor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.ctrade.domain.dto.PriceUpdateMessage;
import com.marmitt.ctrade.infrastructure.exchange.binance.dto.BinanceTickerMessage;
import com.marmitt.ctrade.infrastructure.websocket.processor.StreamProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Processor específico para streams de ticker da Binance.
 * Processa dados de 24hr ticker statistics (!ticker@arr).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TickerStreamProcessor implements StreamProcessor<PriceUpdateMessage> {
    
    private final ObjectMapper objectMapper;
    
    @Override
    public boolean canProcess(String streamName) {
        return "!ticker@arr".equals(streamName) || streamName.endsWith("@ticker");
    }
    
    @Override
    public Optional<PriceUpdateMessage> process(JsonNode data) {
        try {
            List<BinanceTickerMessage> tickerMessages = parseTickerData(data);
            
            log.debug("Processing {} ticker messages", tickerMessages.size());
            
            for (BinanceTickerMessage binanceMessage : tickerMessages) {
                if ("24hrTicker".equals(binanceMessage.getEventType())) {
                    return Optional.of(createPriceUpdate(binanceMessage));
                }
            }
        } catch (Exception e) {
            log.error("Error processing ticker stream data: {}", e.getMessage(), e);
        }
        return Optional.empty();
    }
    
    @Override
    public String getStreamName() {
        return "!ticker@arr";
    }
    
    /**
     * Converte os dados recebidos para lista de BinanceTickerMessage.
     * Suporta tanto JsonNode (streams multiplexados) quanto List (streams diretos).
     */
    private List<BinanceTickerMessage> parseTickerData(JsonNode data) throws Exception {
        return objectMapper.convertValue(data, new TypeReference<List<BinanceTickerMessage>>() {});
    }
    
    /**
     * Cria uma mensagem de atualização de preço a partir dos dados da Binance.
     */
    private PriceUpdateMessage createPriceUpdate(BinanceTickerMessage binanceMessage) {
        PriceUpdateMessage priceUpdate = new PriceUpdateMessage();
        priceUpdate.setTradingPair(binanceMessage.getSymbol());
        priceUpdate.setPrice(binanceMessage.getCurrentPrice());
        priceUpdate.setTimestamp(LocalDateTime.now());
        return priceUpdate;
    }
}