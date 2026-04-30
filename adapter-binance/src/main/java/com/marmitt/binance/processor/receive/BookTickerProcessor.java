package com.marmitt.binance.processor.receive;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.binance.event.BookTickerEvent;
import com.marmitt.core.domain.Symbol;
import com.marmitt.core.dto.processing.ProcessingResult;
import com.marmitt.core.dto.websocket.MessageContext;
import com.marmitt.core.dto.websocket.data.MarketDataDto;
import com.marmitt.core.dto.websocket.data.ProcessorResponse;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.Instant;

@Slf4j
class BookTickerProcessor implements BinanceEventProcessor<MarketDataDto> {

    private final ObjectMapper objectMapper;

    BookTickerProcessor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * bookTicker events have no "e" field. The dispatcher routes them here
     * via a dedicated structural check, not by event type.
     */
    @Override
    public String eventType() {
        return null;
    }

    @Override
    public ProcessingResult<? extends ProcessorResponse> process(JsonNode data, MessageContext context) {
        try {
            BookTickerEvent event = objectMapper.treeToValue(data, BookTickerEvent.class);
            MarketDataDto marketData = new MarketDataDto(
                    context.exchangeName(),
                    Symbol.of(event.s()),
                    event.getBestBidPriceAsDecimal()
                            .add(event.getBestAskPriceAsDecimal())
                            .divide(BigDecimal.valueOf(2)),
                    event.getBestBidPriceAsDecimal(),
                    event.getBestAskPriceAsDecimal(),
                    BigDecimal.ZERO,
                    null, null, null, null,
                    Instant.now()
            );
            return ProcessingResult.success(context.correlationId().toString(), data.toString(), marketData);
        } catch (Exception e) {
            log.error("Failed to process bookTicker: correlationId={}", context.correlationId(), e);
            return ProcessingResult.error(context.correlationId().toString(),
                    "Failed to parse bookTicker: " + e.getMessage(), data.toString(), e);
        }
    }
}
