package com.marmitt.binance.processor.receive;

import com.fasterxml.jackson.databind.JsonNode;
import com.marmitt.core.dto.processing.ProcessingResult;
import com.marmitt.core.dto.websocket.MessageContext;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.dto.websocket.data.ProcessorResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class OrderApiResponseProcessor {

    ProcessingResult<? extends ProcessorResponse> process(
            JsonNode root, JsonNode result, int status,
            String correlationId, String rawMessage, MessageContext context) {
        try {
            OrderDataDto dto;
            if (status == 200) {
                if (!result.has("symbol")) {
                    log.warn("Order API response status=200 but no symbol in result — treating as ignored: correlationId={}",
                            correlationId);
                    return ProcessingResult.ignored(correlationId, "order-response-no-symbol");
                }
                dto = BinanceOrderApiResponseMapper.fromResult(result);
            } else {
                dto = BinanceOrderApiResponseMapper.fromError(root);
            }
            log.debug("Order API response processed: clientOrderId={} status={} correlationId={}",
                    dto.clientOrderId(), dto.status(), correlationId);
            return ProcessingResult.success(correlationId, rawMessage, dto);
        } catch (Exception e) {
            log.error("Failed to parse order API response: correlationId={}", correlationId, e);
            return ProcessingResult.error(correlationId,
                    "Failed to parse order API response: " + e.getMessage(), rawMessage, e);
        }
    }
}
