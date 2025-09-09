package com.marmitt.handler;

import com.marmitt.core.domain.data.ProcessorResponse;
import com.marmitt.core.dto.processing.ProcessingResult;
import com.marmitt.core.ports.outbound.websocket.MessageProcessorPort;
import com.marmitt.event.RawMessageReceivedEvent;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class MessageEventHandler {

    private final MessageProcessorPort binanceMessageProcessor;

    public MessageEventHandler(MessageProcessorPort binanceMessageProcessor) {
        this.binanceMessageProcessor = binanceMessageProcessor;
    }

    @EventListener
    @Async("messageProcessingExecutor")
    public void handleRawMessage(RawMessageReceivedEvent event) {
//         Recupera correlationId no MDC para logs correlacionados
        MDC.put("correlationId", event.getContext().correlationId().toString());
        
        try {
            log.info("Processing message: exchange={}, correlationId={}, connectionId={}, messageLength={}",
                    event.getContext().exchangeName(),
                    event.getContext().correlationId(),
                    event.getContext().connectionId(),
                    event.getRawMessage().length());
            
            // Log temporário para capturar formato da mensagem Binance
            if ("BINANCE".equals(event.getContext().exchangeName())) {
                log.info("BINANCE MESSAGE SAMPLE: {}", event.getRawMessage());
                try {
                    ProcessingResult<? extends ProcessorResponse> processingResult = binanceMessageProcessor.processMessage(event.getRawMessage(), event.getContext());
                    log.info("processingResult: {}", processingResult);
                    
                    if (processingResult.getData().isPresent()) {
                        log.info("Processing SUCCESS - Data type: {}, Data: {}", 
                                processingResult.getData().get().getClass().getSimpleName(),
                                processingResult.getData().get());
                    } else if (processingResult.getErrorMessage().isPresent()) {
                        log.warn("Processing ERROR - Message: {}", processingResult.getErrorMessage().get());
                    }
                } catch (Exception e) {
                    log.error("Error calling BinanceMessageProcessor: {}", e.getMessage(), e);
                }
            }
            
            // TODO: Aqui será implementado o messageDispatcher.findProcessor()
            // TODO: Aqui será implementado o processor.processMessage()
            
            // Por enquanto, apenas loga a mensagem processada
            log.info("Message processing completed successfully");
            
        } catch (Exception e) {
            log.error("Error processing message: {}", e.getMessage(), e);
            
        } finally {
            MDC.clear(); // Limpa MDC
        }
    }
}