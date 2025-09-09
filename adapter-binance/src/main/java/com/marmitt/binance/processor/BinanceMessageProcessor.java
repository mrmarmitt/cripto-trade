package com.marmitt.binance.processor;

import com.marmitt.core.domain.data.ErrorData;
import com.marmitt.core.domain.data.ProcessorResponse;
import com.marmitt.core.dto.processing.ProcessingResult;
import com.marmitt.core.dto.websocket.MessageContext;
import com.marmitt.core.ports.outbound.websocket.MessageProcessorPort;
import com.marmitt.core.ports.outbound.websocket.SpecializedProcessor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class BinanceMessageProcessor implements MessageProcessorPort {

    private final List<SpecializedProcessor<? extends ProcessorResponse>> specializedProcessors;

    public BinanceMessageProcessor() {
        this.specializedProcessors = List.of(
                new BinanceTickerProcessor()
        );
    }

    @Override
    public ProcessingResult<? extends ProcessorResponse> processMessage(String rawMessage, MessageContext context) {
        log.info("Binance - Processing message: correlationId={}, length={}",
                context.correlationId(), rawMessage.length());

        for (SpecializedProcessor<? extends ProcessorResponse> processor : specializedProcessors) {
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
