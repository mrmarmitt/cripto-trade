package com.marmitt.ctrade.infrastructure.exchange.binance.strategy.processor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.ctrade.domain.dto.PriceUpdateMessage;
import com.marmitt.ctrade.infrastructure.exchange.binance.dto.BinanceTickerMessage;
import com.marmitt.ctrade.domain.strategy.processor.StreamProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Processor específico para streams de ticker da Binance.
 * Processa dados de 24hr ticker statistics via subscrição direta de símbolos.
 */
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
                    String symbol = binanceMessage.getSymbol();
                    log.debug("Processing symbol: {}", symbol);
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
        return "ticker";
    }
    
    /**
     * Converte os dados recebidos para lista de BinanceTickerMessage.
     * Suporta tanto array (streams !ticker@arr) quanto objeto único (streams individuais @ticker).
     */
    private List<BinanceTickerMessage> parseTickerData(JsonNode data) throws Exception {
        if (data.isArray()) {
            // Caso de array: !ticker@arr ou múltiplos streams
            return objectMapper.convertValue(data, new TypeReference<List<BinanceTickerMessage>>() {});
        } else {
            // Caso de objeto único: stream individual como btcusdc@ticker
            BinanceTickerMessage tickerMessage = objectMapper.convertValue(data, BinanceTickerMessage.class);
            return List.of(tickerMessage);
        }
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