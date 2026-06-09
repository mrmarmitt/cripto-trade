package com.marmitt.core.application.handler.connection;

import com.marmitt.core.dto.events.WebSocketFailedEvent;
import com.marmitt.core.ports.inbound.handler.CriticalConnectionFailedPort;
import com.marmitt.core.ports.inbound.runner.HaltRunnerPort;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CriticalConnectionFailureHandler implements CriticalConnectionFailedPort {

    private final HaltRunnerPort haltRunnerPort;

    public CriticalConnectionFailureHandler(HaltRunnerPort haltRunnerPort) {
        this.haltRunnerPort = haltRunnerPort;
    }

    @Override
    public void execute(WebSocketFailedEvent event) {
        if (!event.isCritical()) {
            return;
        }
        log.error("Critical connection failure — exchange={} channel={} after {} attempts. Halting all runners.",
                event.exchange(), event.channel(), event.attemptCount());
        haltRunnerPort.haltAll();
    }
}
