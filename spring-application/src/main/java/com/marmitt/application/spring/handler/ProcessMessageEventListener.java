package com.marmitt.application.spring.handler;

import com.marmitt.application.spring.event.RawMarketMessageReceivedEvent;
import com.marmitt.core.dto.processing.ProcessingResult;
import com.marmitt.core.ports.inbound.handler.HandlerProcessMessagePort;
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
    public void handleRawMessage(RawMarketMessageReceivedEvent event) {
        MDC.put("correlationId", event.getContext().correlationId().toString());
        MDC.put("exchangeName", event.getContext().exchangeName());
        MDC.put("connectionId", event.getContext().connectionId().toString());

        try {
            ProcessingResult<?> result = processMessagePort.execute(event.getRawMessage(), event.getContext());

            if (result.isSuccess() && result.getData().isPresent()) {
                log.info("Processing SUCCESS - type={} correlationId={}",
                        result.getData().get().getClass().getSimpleName(),
                        event.getContext().correlationId());
            } else if (result.isWarning() && result.getData().isPresent()) {
                log.warn("Processing WARNING - type={} warning={} rawMessage={}",
                        result.getData().get().getClass().getSimpleName(),
                        result.getErrorMessage().orElse("unknown"),
                        result.getRawMessage().orElse("none"));
            } else if (result.isError()) {
                log.error("Processing ERROR - message={} rawMessage={}",
                        result.getErrorMessage().orElse("unknown"),
                        result.getRawMessage().orElse("none"));
            }
        } catch (Exception e) {
            log.error("Error processing market message: {}", e.getMessage(), e);
        } finally {
            MDC.clear();
        }
    }
}
