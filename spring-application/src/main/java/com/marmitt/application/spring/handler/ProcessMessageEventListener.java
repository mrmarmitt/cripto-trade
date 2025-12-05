package com.marmitt.application.spring.handler;

import com.marmitt.core.dto.processing.ProcessingResult;
import com.marmitt.core.ports.inbound.handler.HandlerProcessMessagePort;
import com.marmitt.application.spring.event.RawMessageReceivedEvent;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ProcessMessageEventListener {

    private final HandlerProcessMessagePort processMessagePort;

    public ProcessMessageEventListener(HandlerProcessMessagePort processMessagePort) {
        this.processMessagePort = processMessagePort;
    }

    @EventListener
    @Async("messageProcessingExecutor")
    public void handleRawMessage(RawMessageReceivedEvent event) {
        // Adiciona informações de contexto no MDC para logs correlacionados
        MDC.put("correlationId", event.getContext().correlationId().toString());
        MDC.put("exchangeName", event.getContext().exchangeName());
        MDC.put("connectionId", event.getContext().connectionId().toString());
        
        try {
            // Delega o processamento para o UseCase
            ProcessingResult<?> result = processMessagePort.execute(
                event.getRawMessage(), 
                event.getContext()
            );
            
            // Log do resultado diferenciando por tipo de ProcessingResult
            if (result.isSuccess() && result.getData().isPresent()) {
                log.info("Processing SUCCESS - Data type: {}, Data: {}", 
                        result.getData().get().getClass().getSimpleName(), 
                        result.getData().get());
            } else if (result.isWarning() && result.getData().isPresent()) {
                log.warn("Processing WARNING - Data type: {}, Warning: {}, , Received Message: {}, Data: {}",
                        result.getData().get().getClass().getSimpleName(),
                        result.getErrorMessage().orElse("No warning errorMessage"),
                        result.getRawMessage().orElse("No received errorMessage"),
                        result.getData().get());
            } else if (result.isError()) {
                log.error("Processing ERROR - Message: {}, Received Message: {}",
                        result.getErrorMessage().orElse("No error errorMessage"),
                        result.getRawMessage().orElse("No received errorMessage"));
            }

        } catch (Exception e) {
            log.error("Error processing errorMessage: {}", e.getMessage(), e);
            
        } finally {
            MDC.clear(); // Limpa MDC
        }
    }
}