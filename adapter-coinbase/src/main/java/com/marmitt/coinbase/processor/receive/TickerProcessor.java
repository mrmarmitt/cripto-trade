package com.marmitt.coinbase.processor.receive;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.coinbase.event.TickerEvent;
import com.marmitt.core.domain.Symbol;
import com.marmitt.core.dto.processing.ProcessingResult;
import com.marmitt.core.dto.websocket.MessageContext;
import com.marmitt.core.dto.websocket.data.MarketDataDto;
import com.marmitt.core.dto.websocket.data.ProcessorResponse;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Slf4j
class TickerProcessor implements CoinbaseEventProcessor<MarketDataDto> {

    private final ObjectMapper objectMapper;

    TickerProcessor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String eventType() {
        return "ticker";
    }

    @Override
    public ProcessingResult<? extends ProcessorResponse> process(JsonNode data, MessageContext context) {
        String correlationId = context.correlationId().toString();
        try {
            TickerEvent tickerEvent = objectMapper.treeToValue(data, TickerEvent.class);
            MarketDataDto marketData = convertTickerEventToMarketData(tickerEvent, context);

            if (!isValidMarketData(marketData)) {
                return ProcessingResult.warning(
                        correlationId,
                        data.toString(),
                        marketData,
                        "Coinbase ticker contains suspicious values: price=" + marketData.price());
            }

            log.debug("Successfully processed Coinbase ticker for currency: {}", marketData.symbol().value());
            return ProcessingResult.success(correlationId, data.toString(), marketData);

        } catch (Exception e) {
            log.error("Error processing Coinbase ticker: correlationId={}, error={}", correlationId, e.getMessage(), e);
            return ProcessingResult.error(correlationId, "Failed to parse Coinbase ticker: " + e.getMessage(), data.toString(), e);
        }
    }

    private MarketDataDto convertTickerEventToMarketData(TickerEvent tickerEvent, MessageContext context) {
        Symbol symbol = Symbol.of(tickerEvent.product_id());
        BigDecimal price = tickerEvent.getLastPriceAsDecimal();

        BigDecimal bidPrice = tickerEvent.best_bid() != null ? tickerEvent.getBestBidPriceAsDecimal() : null;
        BigDecimal askPrice = tickerEvent.best_ask() != null ? tickerEvent.getBestAskPriceAsDecimal() : null;
        BigDecimal volume = tickerEvent.volume_24h() != null ? new BigDecimal(tickerEvent.volume_24h()) : BigDecimal.ZERO;
        BigDecimal high24h = tickerEvent.high_24h() != null ? new BigDecimal(tickerEvent.high_24h()) : null;
        BigDecimal low24h = tickerEvent.low_24h() != null ? new BigDecimal(tickerEvent.low_24h()) : null;

        BigDecimal priceChange24h = null;
        BigDecimal priceChangePercent24h = null;
        if (tickerEvent.open_24h() != null) {
            BigDecimal open24h = new BigDecimal(tickerEvent.open_24h());
            priceChange24h = price.subtract(open24h);
            if (open24h.compareTo(BigDecimal.ZERO) > 0) {
                priceChangePercent24h = priceChange24h
                        .divide(open24h, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));
            }
        }

        return new MarketDataDto(
                context.exchangeName(),
                symbol, price, bidPrice, askPrice, volume,
                high24h, low24h, priceChange24h, priceChangePercent24h,
                Instant.now()
        );
    }

    private boolean isValidMarketData(MarketDataDto marketData) {
        if (marketData.price() == null || marketData.price().compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Invalid price in Coinbase ticker: {}", marketData.price());
            return false;
        }

        BigDecimal maxPrice = new BigDecimal("10000000");
        if (marketData.price().compareTo(maxPrice) > 0) {
            log.warn("Suspicious high price in Coinbase ticker: {}", marketData.price());
            return false;
        }

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

        if (marketData.volume() != null && marketData.volume().compareTo(BigDecimal.ZERO) < 0) {
            log.warn("Negative volume in Coinbase ticker: {}", marketData.volume());
            return false;
        }

        return true;
    }
}
