package com.marmitt.coinbase.processor.receive;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.coinbase.event.TickerEvent;
import com.marmitt.core.domain.data.MarketData;
import com.marmitt.core.domain.Symbol;
import com.marmitt.core.dto.processing.ProcessingResult;
import com.marmitt.core.dto.websocket.MessageContext;
import com.marmitt.core.ports.outbound.exchange.adapter.ReceivedSpecializedProcessorPort;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Slf4j
public class TickerProcessor implements ReceivedSpecializedProcessorPort<MarketData> {

    private final ObjectMapper objectMapper;

    public TickerProcessor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ProcessingResult<MarketData> processMessage(String rawMessage, MessageContext context) {
        String correlationId = context.correlationId().toString();
        
        try {
            TickerEvent tickerEvent = objectMapper.readValue(rawMessage, TickerEvent.class);
            
            // Converter Coinbase ticker para MarketData
            MarketData marketData = convertTickerEventToMarketData(tickerEvent);
            
            // Validações básicas de sanidade
            if (!isValidMarketData(marketData)) {
                return ProcessingResult.warning(
                        correlationId,
                        rawMessage,
                        marketData,
                        "Coinbase ticker contains suspicious values: price=" + marketData.price());
            }
            
            log.debug("Successfully processed Coinbase ticker for symbol: {}", marketData.symbol().value());
            return ProcessingResult.success(correlationId, rawMessage, marketData);
            
        } catch (Exception e) {
            log.error("Error processing Coinbase ticker: correlationId={}, error={}", 
                     correlationId, e.getMessage(), e);
            
            return createErrorResult(correlationId, "Failed to parse Coinbase ticker: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean canProcess(String rawMessage) {
        try {
            JsonNode json = objectMapper.readTree(rawMessage);
            
            // Coinbase ticker format: {"type":"ticker","product_id":"BTC-USD","price":"43250.00",...}
            // Verifica se é um ticker da Coinbase
            return json.has("type") && 
                   "ticker".equals(json.get("type").asText()) &&
                   json.has("product_id") && 
                   json.has("price");
            
        } catch (Exception e) {
            log.trace("Cannot process as Coinbase ticker: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Converte TickerEvent da Coinbase para MarketData do domínio.
     */
    private MarketData convertTickerEventToMarketData(TickerEvent tickerEvent) {
        // Symbol: Coinbase usa formato "BTC-USD", convertemos para Symbol
        Symbol symbol = Symbol.of(tickerEvent.product_id());
        
        // Preço atual (obrigatório)
        BigDecimal price = tickerEvent.getLastPriceAsDecimal();
        
        // Campos opcionais com fallbacks seguros
        BigDecimal bidPrice = tickerEvent.best_bid() != null ? 
            tickerEvent.getBestBidPriceAsDecimal() : null;
        BigDecimal askPrice = tickerEvent.best_ask() != null ? 
            tickerEvent.getBestAskPriceAsDecimal() : null;
        BigDecimal volume = tickerEvent.volume_24h() != null ? 
            new BigDecimal(tickerEvent.volume_24h()) : BigDecimal.ZERO;
        BigDecimal high24h = tickerEvent.high_24h() != null ? 
            new BigDecimal(tickerEvent.high_24h()) : null;
        BigDecimal low24h = tickerEvent.low_24h() != null ? 
            new BigDecimal(tickerEvent.low_24h()) : null;
        
        // Calcular change e change percent baseado no open_24h
        BigDecimal priceChange24h = null;
        BigDecimal priceChangePercent24h = null;
        if (tickerEvent.open_24h() != null) {
            BigDecimal open24h = new BigDecimal(tickerEvent.open_24h());
            priceChange24h = price.subtract(open24h);
            
            // Evitar divisão por zero
            if (open24h.compareTo(BigDecimal.ZERO) > 0) {
                priceChangePercent24h = priceChange24h
                    .divide(open24h, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
            }
        }
        
        return new MarketData(
            symbol, price, bidPrice, askPrice, volume,
            high24h, low24h, priceChange24h, priceChangePercent24h,
            Instant.now()
        );
    }
    
    /**
     * Valida se os dados do market data são consistentes e realistas.
     */
    private boolean isValidMarketData(MarketData marketData) {
        // Validação: preço deve ser positivo
        if (marketData.price() == null || marketData.price().compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Invalid price in Coinbase ticker: {}", marketData.price());
            return false;
        }
        
        // Validação: preço muito alto (possível erro de parsing)
        BigDecimal maxPrice = new BigDecimal("10000000"); // 10 milhões
        if (marketData.price().compareTo(maxPrice) > 0) {
            log.warn("Suspicious high price in Coinbase ticker: {}", marketData.price());
            return false;
        }
        
        // Validação: spread bid/ask muito alto (> 15% é suspeito para crypto)
        if (marketData.bidPrice() != null && marketData.askPrice() != null) {
            BigDecimal spread = marketData.getSpread();
            BigDecimal spreadPercent = spread
                .divide(marketData.price(), 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
            
            if (spreadPercent.compareTo(new BigDecimal("15")) > 0) {
                log.warn("Suspicious spread in Coinbase ticker: {}%", spreadPercent);
                return false;
            }
        }
        
        // Validação: volume negativo
        if (marketData.volume() != null && marketData.volume().compareTo(BigDecimal.ZERO) < 0) {
            log.warn("Negative volume in Coinbase ticker: {}", marketData.volume());
            return false;
        }
        
        return true;
    }
    
    /**
     * Cria resultado de erro com tipo correto.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private ProcessingResult<MarketData> createErrorResult(String correlationId, String message, Exception e) {
        ProcessingResult error = new ProcessingResult.Error(
            correlationId, message, null, e, Instant.now()
        );
        return (ProcessingResult<MarketData>) error;
    }
}