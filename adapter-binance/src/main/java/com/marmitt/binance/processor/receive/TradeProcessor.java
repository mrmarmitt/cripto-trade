package com.marmitt.binance.processor.receive;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.binance.event.TradeEvent;
import com.marmitt.core.domain.Symbol;
import com.marmitt.core.dto.processing.ProcessingResult;
import com.marmitt.core.dto.websocket.MessageContext;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.dto.websocket.data.TradeDataDto;
import com.marmitt.core.dto.websocket.data.ProcessorResponse;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;

@Slf4j
class TradeProcessor implements BinanceEventProcessor<TradeDataDto> {

    private final ObjectMapper objectMapper;

    TradeProcessor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String eventType() {
        return "trade";
    }

    @Override
    public ProcessingResult<? extends ProcessorResponse> process(JsonNode data, MessageContext context) {
        try {
            TradeEvent event = objectMapper.treeToValue(data, TradeEvent.class);
            TradeDataDto tradeData = new TradeDataDto(
                    String.valueOf(event.t()),
                    Symbol.of(event.s()),
                    event.getPriceAsDecimal(),
                    event.getQuantityAsDecimal(),
                    event.isBuyerMarketMaker() ? OrderDataDto.OrderSide.SELL : OrderDataDto.OrderSide.BUY,
                    String.valueOf(event.b()),
                    String.valueOf(event.a()),
                    null,
                    null,
                    Instant.ofEpochMilli(event.T())
            );
            return ProcessingResult.success(context.correlationId().toString(), data.toString(), tradeData);
        } catch (Exception e) {
            log.error("Failed to process trade: correlationId={}", context.correlationId(), e);
            return ProcessingResult.error(context.correlationId().toString(),
                    "Failed to parse trade: " + e.getMessage(), data.toString(), e);
        }
    }
}
