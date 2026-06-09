package com.marmitt.core.ports.inbound.handler;

import com.marmitt.core.dto.events.WebSocketFailedEvent;

public interface CriticalConnectionFailedPort {

    void execute(WebSocketFailedEvent event);
}
