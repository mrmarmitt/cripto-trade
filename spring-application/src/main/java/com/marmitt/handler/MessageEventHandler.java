package com.marmitt.handler;

import com.marmitt.event.RawMessageReceivedEvent;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class MessageEventHandler {
    
    @EventListener
    @Async("messageProcessingExecutor")
    public void handleRawMessage(RawMessageReceivedEvent event) {
        // Recupera correlationId no MDC para logs correlacionados
       // MDC.put("correlationId", event.getContext().correlationId());
        
        try {
            log.info("Processing message: exchange={}, correlationId={}, connectionId={}, messageLength={}",
                    event.getContext().exchangeName(),
                    event.getContext().correlationId(),
                    event.getContext().connectionId(),
                    event.getRawMessage().length());
            
            // TODO: Aqui será implementado o messageDispatcher.findProcessor()
            // TODO: Aqui será implementado o processor.processMessage()
            
            // Por enquanto, apenas loga a mensagem processada
            log.info("Message processing completed successfully");
            
        } catch (Exception e) {
            log.error("Error processing message: {}", e.getMessage(), e);
            
//        } finally {
          //  MDC.clear(); // Limpa MDC
        }
    }
}