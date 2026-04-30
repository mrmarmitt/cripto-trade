package com.marmitt.binance.processor.receive;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.binance.event.TickerEvent;
import com.marmitt.core.domain.Symbol;
import com.marmitt.core.dto.processing.ProcessingResult;
import com.marmitt.core.dto.websocket.MessageContext;
import com.marmitt.core.dto.websocket.data.MarketDataDto;
import com.marmitt.core.dto.websocket.data.ProcessorResponse;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.Instant;

@Slf4j
class TickerProcessor implements BinanceEventProcessor<MarketDataDto> {

    private final ObjectMapper objectMapper;

    TickerProcessor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String eventType() {
        return "24hrTicker";
    }

    @Override
    public ProcessingResult<? extends ProcessorResponse> process(JsonNode data, MessageContext context) {
        try {
            TickerEvent event = objectMapper.treeToValue(data, TickerEvent.class);
            MarketDataDto marketData = toMarketData(event, context);
            return ProcessingResult.success(context.correlationId().toString(), data.toString(), marketData);
        } catch (Exception e) {
            log.error("Failed to process 24hrTicker: correlationId={}", context.correlationId(), e);
            return ProcessingResult.error(context.correlationId().toString(),
                    "Failed to parse 24hrTicker: " + e.getMessage(), data.toString(), e);
        }
    }

    private MarketDataDto toMarketData(TickerEvent event, MessageContext context) {
        return new MarketDataDto(
                context.exchangeName(),
                Symbol.of(event.s()),
                new BigDecimal(event.c()),
                event.b() != null ? new BigDecimal(event.b()) : null,
                event.a() != null ? new BigDecimal(event.a()) : null,
                event.v() != null ? new BigDecimal(event.v()) : BigDecimal.ZERO,
                event.h() != null ? new BigDecimal(event.h()) : null,
                event.l() != null ? new BigDecimal(event.l()) : null,
                event.p() != null ? new BigDecimal(event.p()) : null,
                event.P() != null ? new BigDecimal(event.P()) : null,
                Instant.now()
        );
    }
}
