package com.marmitt.binance.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.binance.event.TickerEvent;
import com.marmitt.core.domain.data.MarketData;
import com.marmitt.core.domain.Symbol;
import com.marmitt.core.dto.processing.ProcessingResult;
import com.marmitt.core.dto.websocket.MessageContext;
import com.marmitt.core.ports.outbound.websocket.SpecializedProcessor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.Instant;

@Slf4j
public class BinanceTickerProcessor implements SpecializedProcessor<MarketData> {
    
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public ProcessingResult<MarketData> processMessage(String rawMessage, MessageContext context) {
        String correlationId = context.correlationId().toString();
        
        try {
            log.debug("Processing Binance ticker message: correlationId={}", correlationId);
            
            TickerEvent tickerEvent = objectMapper.readValue(rawMessage, TickerEvent.class);
            
            // Parse campos específicos Binance ticker
            MarketData marketData = convertTickerEventToMarketData(tickerEvent);
            
            // Validações básicas
            if (!isValidMarketData(marketData)) {
                return ProcessingResult.warning(correlationId, marketData,
                    "Binance ticker contains suspicious values: price=" + marketData.price());
            }
            
            log.debug("Successfully processed Binance ticker: symbol={}, price={}, correlationId={}", 
                     marketData.symbol().value(), marketData.price(), correlationId);
            
            return ProcessingResult.success(correlationId, marketData);
            
        } catch (Exception e) {
            log.error("Error processing Binance ticker: correlationId={}, error={}", 
                     correlationId, e.getMessage(), e);
            
            return createErrorResult(correlationId, "Failed to parse Binance ticker: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean canProcess(String rawMessage) {
        try {
            JsonNode json = objectMapper.readTree(rawMessage);
            
            // Binance ticker 24hr format: {"s":"BTCUSDT","c":"43250.00","o":"42100.00",...}
            // Verifica se tem symbol (s) e close price (c)
            return json.has("s") && json.has("c") && 
                   // Pode também ter event type "24hrTicker" ou outros campos típicos
                   (json.has("e") || json.has("P") || json.has("v"));
            
        } catch (Exception e) {
            log.trace("Cannot process as Binance ticker: {}", e.getMessage());
            return false;
        }
    }
    
    private MarketData convertTickerEventToMarketData(TickerEvent tickerEvent) {
        Symbol symbol = Symbol.of(tickerEvent.s());
        BigDecimal price = tickerEvent.getLastPriceAsDecimal();
        
        // Campos opcionais com fallbacks
        BigDecimal bidPrice = tickerEvent.b() != null ? tickerEvent.getBestBidPriceAsDecimal() : null;
        BigDecimal askPrice = tickerEvent.a() != null ? tickerEvent.getBestAskPriceAsDecimal() : null;
        BigDecimal volume = tickerEvent.v() != null ? new BigDecimal(tickerEvent.v()) : BigDecimal.ZERO;
        BigDecimal high24h = tickerEvent.h() != null ? new BigDecimal(tickerEvent.h()) : null;
        BigDecimal low24h = tickerEvent.l() != null ? new BigDecimal(tickerEvent.l()) : null;
        BigDecimal priceChange24h = tickerEvent.p() != null ? new BigDecimal(tickerEvent.p()) : null;
        BigDecimal priceChangePercent24h = tickerEvent.P() != null ? new BigDecimal(tickerEvent.P()) : null;
        
        return new MarketData(
            symbol, price, bidPrice, askPrice, volume,
            high24h, low24h, priceChange24h, priceChangePercent24h,
            Instant.now()
        );
    }
    
    private boolean isValidMarketData(MarketData marketData) {
        // Validações básicas de sanidade
        if (marketData.price() == null || marketData.price().compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        
        // Preço muito alto para criptomoedas (possível erro)
        if (marketData.price().compareTo(new BigDecimal("10000000")) > 0) {
            return false;
        }
        
        // Se tem bid/ask, verifica spread razoável
        if (marketData.bidPrice() != null && marketData.askPrice() != null) {
            BigDecimal spread = marketData.getSpread();
            BigDecimal spreadPercent = spread.divide(marketData.price(), 4, BigDecimal.ROUND_HALF_UP)
                                            .multiply(new BigDecimal("100"));
            
            // Spread > 10% é suspeito
            if (spreadPercent.compareTo(new BigDecimal("10")) > 0) {
                return false;
            }
        }
        
        return true;
    }
    
    @SuppressWarnings({"unchecked", "rawtypes"})
    private ProcessingResult<MarketData> createErrorResult(String correlationId, String message, Exception e) {
        ProcessingResult error = new ProcessingResult.Error(correlationId, message, e, java.time.Instant.now());
        return (ProcessingResult<MarketData>) error;
    }
}
