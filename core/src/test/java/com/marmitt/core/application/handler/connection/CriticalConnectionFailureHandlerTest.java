package com.marmitt.core.application.handler.connection;

import com.marmitt.core.dto.events.WebSocketFailedEvent;
import com.marmitt.core.dto.runner.response.RunnerHaltResult;
import com.marmitt.core.enums.StreamChannel;
import com.marmitt.core.ports.inbound.runner.HaltRunnerPort;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CriticalConnectionFailureHandlerTest {

    @Test
    void critical_event_triggers_haltAll() {
        RecordingHaltRunnerPort haltPort = new RecordingHaltRunnerPort();
        CriticalConnectionFailureHandler handler = new CriticalConnectionFailureHandler(haltPort);

        WebSocketFailedEvent critical = WebSocketFailedEvent.withAttempts(
                "BINANCE", "Max attempts", null, null, 11, StreamChannel.MARKET);

        assertTrue(critical.isCritical());
        handler.execute(critical);

        assertTrue(haltPort.haltAllCalled, "haltAll must be called on critical failure");
    }

    @Test
    void non_critical_event_does_not_trigger_haltAll() {
        RecordingHaltRunnerPort haltPort = new RecordingHaltRunnerPort();
        CriticalConnectionFailureHandler handler = new CriticalConnectionFailureHandler(haltPort);

        WebSocketFailedEvent nonCritical = WebSocketFailedEvent.of(
                "BINANCE", "Connection dropped", UUID.randomUUID(), null, StreamChannel.MARKET);

        assertFalse(nonCritical.isCritical());
        handler.execute(nonCritical);

        assertFalse(haltPort.haltAllCalled, "haltAll must NOT be called for non-critical failure");
    }

    static class RecordingHaltRunnerPort implements HaltRunnerPort {
        boolean haltAllCalled = false;

        @Override
        public List<RunnerHaltResult> haltAll() {
            haltAllCalled = true;
            return new ArrayList<>();
        }

        @Override
        public RunnerHaltResult haltById(UUID id) {
            return null;
        }
    }
}
