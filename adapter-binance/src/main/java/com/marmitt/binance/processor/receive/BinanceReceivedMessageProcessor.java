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
        for (ReceivedSpecializedProcessorPort<? extends ProcessorResponse> processor : specializedProcessors) {
            if (processor.canProcess(rawMessage)) {
                try {
                    return processor.processMessage(rawMessage, context);
                } catch (Exception e) {
                    return ProcessingResult.error(
                            context.correlationId().toString(),
                            "Error in specialized processor " + processor.getClass().getSimpleName() + ", error=" + e.getMessage(),
                            rawMessage,
                            e);
                }
            }
        }

        return ProcessingResult.error(context.correlationId().toString(),
                "No specialized processor found for Binance message",
                rawMessage
                );
    }
}
