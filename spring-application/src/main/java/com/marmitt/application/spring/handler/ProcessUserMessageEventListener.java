package com.marmitt.application.spring.handler;

import com.marmitt.application.spring.event.RawUserDataMessageReceivedEvent;
import com.marmitt.core.dto.processing.ProcessingResult;
import com.marmitt.core.ports.inbound.handler.HandlerProcessUserMessagePort;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ProcessUserMessageEventListener {

    private final HandlerProcessUserMessagePort processUserMessagePort;

    public ProcessUserMessageEventListener(HandlerProcessUserMessagePort processUserMessagePort) {
        this.processUserMessagePort = processUserMessagePort;
    }

    @EventListener
    @Async("messageProcessingExecutor")
    public void handleRawUserDataMessage(RawUserDataMessageReceivedEvent event) {
        MDC.put("correlationId", event.getContext().correlationId().toString());
        MDC.put("exchangeName", event.getContext().exchangeName());
        MDC.put("connectionId", event.getContext().connectionId().toString());

        try {
            ProcessingResult<?> result = processUserMessagePort.execute(event.getRawMessage(), event.getContext());

            if (result.isSuccess() && result.getData().isPresent()) {
                log.debug("User data processing SUCCESS - type={}", result.getData().get().getClass().getSimpleName());
            } else if (result.isWarning() && result.getData().isPresent()) {
                log.warn("User data processing WARNING - warning={} rawMessage={}",
                        result.getErrorMessage().orElse("unknown"),
                        result.getRawMessage().orElse("none"));
            } else if (result.isError()) {
                log.error("User data processing ERROR - message={} rawMessage={}",
                        result.getErrorMessage().orElse("unknown"),
                        result.getRawMessage().orElse("none"));
            }
        } catch (Exception e) {
            log.error("Error processing user data message: {}", e.getMessage(), e);
        } finally {
            MDC.clear();
        }
    }
}
