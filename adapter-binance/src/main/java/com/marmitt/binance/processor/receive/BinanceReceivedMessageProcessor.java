package com.marmitt.binance.processor.receive;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.core.domain.data.ProcessorResponse;
import com.marmitt.core.dto.processing.ProcessingResult;
import com.marmitt.core.dto.websocket.MessageContext;
import com.marmitt.core.ports.outbound.exchange.adapter.ReceivedMessageProcessorPort;
import com.marmitt.core.ports.outbound.exchange.adapter.ReceivedSpecializedProcessorPort;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class BinanceReceivedMessageProcessor implements ReceivedMessageProcessorPort {

    private final List<ReceivedSpecializedProcessorPort<? extends ProcessorResponse>> specializedProcessors;

    public BinanceReceivedMessageProcessor(ObjectMapper objectMapper) {
        this.specializedProcessors = List.of(
                new TickerProcessor(objectMapper)
        );
    }

    @Override
    public ProcessingResult<? extends ProcessorResponse> processMessage(String rawMessage, MessageContext context) {
        log.info("Binance - Processing message: correlationId={}, length={}",
                context.correlationId(), rawMessage.length());

        for (ReceivedSpecializedProcessorPort<? extends ProcessorResponse> processor : specializedProcessors) {
            if (processor.canProcess(rawMessage)) {
                try {
                    return processor.processMessage(rawMessage, context);
                } catch (Exception e) {
                    log.error("Error in specialized processor {}: correlationId={}, error={}", 
                             processor.getClass().getSimpleName(), context.correlationId(), e.getMessage(), e);
                    return ProcessingResult.error(context.correlationId().toString(),
                        "Failed in " + processor.getClass().getSimpleName() + ": " + e.getMessage(), e);
                }
            }
        }

        log.warn("No specialized processor found for Binance message: correlationId={}, messageLength={}", 
                context.correlationId(), rawMessage.length());
        return ProcessingResult.error(context.correlationId().toString(),
            "No specialized processor found for Binance message");
    }
}
