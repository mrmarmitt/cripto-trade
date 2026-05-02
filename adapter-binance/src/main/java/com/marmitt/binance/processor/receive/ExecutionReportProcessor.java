package com.marmitt.binance.processor.receive;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.core.domain.Symbol;
import com.marmitt.core.dto.processing.ProcessingResult;
import com.marmitt.core.dto.websocket.MessageContext;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.dto.websocket.data.ProcessorResponse;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.Instant;

@Slf4j
class ExecutionReportProcessor implements BinanceEventProcessor<OrderDataDto> {

    private final ObjectMapper objectMapper;

    ExecutionReportProcessor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String eventType() {
        return "executionReport";
    }

    @Override
    public ProcessingResult<? extends ProcessorResponse> process(JsonNode data, MessageContext context) {
        String correlationId = context.correlationId().toString();
        try {
            String orderId       = data.path("i").asText();
            String clientOrderId = data.path("c").asText();
            String symbolRaw     = data.path("s").asText();
            String sideRaw       = data.path("S").asText();
            String typeRaw       = data.path("o").asText();
            String statusRaw     = data.path("X").asText();
            BigDecimal quantity  = new BigDecimal(data.path("q").asText("0"));
            BigDecimal executedQty  = new BigDecimal(data.path("z").asText("0"));
            BigDecimal price        = new BigDecimal(data.path("p").asText("0"));
            BigDecimal executedPrice = new BigDecimal(data.path("L").asText("0"));
            BigDecimal fee          = new BigDecimal(data.path("n").asText("0"));
            String rejectReason  = data.path("r").asText(null);
            long transactionTime = data.path("T").asLong(0);

            OrderDataDto dto = new OrderDataDto(
                    orderId,
                    clientOrderId,
                    Symbol.of(symbolRaw),
                    mapSide(sideRaw),
                    mapType(typeRaw),
                    quantity,
                    executedQty,
                    price,
                    executedPrice,
                    fee,
                    mapStatus(statusRaw),
                    "NONE".equals(rejectReason) ? null : rejectReason,
                    transactionTime > 0 ? Instant.ofEpochMilli(transactionTime) : Instant.now()
            );

            log.debug("executionReport processed: orderId={} status={} correlationId={}",
                    orderId, statusRaw, correlationId);
            return ProcessingResult.success(correlationId, data.toString(), dto);

        } catch (Exception e) {
            log.error("Failed to parse executionReport: correlationId={}", correlationId, e);
            return ProcessingResult.error(correlationId, "Failed to parse executionReport: " + e.getMessage(), e);
        }
    }

    private OrderDataDto.OrderSide mapSide(String raw) {
        return switch (raw.toUpperCase()) {
            case "SELL" -> OrderDataDto.OrderSide.SELL;
            default     -> OrderDataDto.OrderSide.BUY;
        };
    }

    private OrderDataDto.OrderType mapType(String raw) {
        return switch (raw.toUpperCase()) {
            case "LIMIT"       -> OrderDataDto.OrderType.LIMIT;
            case "STOP"        -> OrderDataDto.OrderType.STOP;
            case "STOP_LOSS_LIMIT", "TAKE_PROFIT_LIMIT" -> OrderDataDto.OrderType.STOP_LIMIT;
            default            -> OrderDataDto.OrderType.MARKET;
        };
    }

    private OrderDataDto.OrderStatus mapStatus(String raw) {
        return switch (raw.toUpperCase()) {
            case "PARTIALLY_FILLED" -> OrderDataDto.OrderStatus.PARTIALLY_FILLED;
            case "FILLED"           -> OrderDataDto.OrderStatus.FILLED;
            case "CANCELED"         -> OrderDataDto.OrderStatus.CANCELED;
            case "REJECTED"         -> OrderDataDto.OrderStatus.REJECTED;
            case "EXPIRED"          -> OrderDataDto.OrderStatus.EXPIRED;
            default                 -> OrderDataDto.OrderStatus.NEW;
        };
    }
}
