package com.marmitt.mock.adapter;

import com.marmitt.core.dto.events.WebSocketConnectedEvent;
import com.marmitt.core.dto.events.WebSocketDisconnectedEvent;
import com.marmitt.core.ports.outbound.events.EventPublisherPort;
import com.marmitt.core.ports.outbound.websocket.WebSocketPort;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Local WebSocket adapter for MOCK exchange.
 *
 * <p>Does not open real sockets, but emits connection lifecycle events so the same
 * post-connection pipeline used by real exchanges can run for mock as well.
 */
@Slf4j
public class LocalEventWebSocketAdapter implements WebSocketPort {

    private final EventPublisherPort eventPublisher;
    private final AtomicBoolean connected = new AtomicBoolean(false);

    public LocalEventWebSocketAdapter(EventPublisherPort eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void connect(String url, String exchangeName, UUID connectionId) {
        connected.set(true);
        log.debug("Mock local WebSocket adapter - connect simulated (exchangeName: {}, connectionId: {}, url: {})",
                exchangeName, connectionId, url);
        eventPublisher.publishEvent(WebSocketConnectedEvent.of(
                exchangeName,
                "Mock local connection established",
                connectionId
        ));
    }

    @Override
    public void disconnect(String exchangeName, UUID currentConnectionId) {
        connected.set(false);
        log.debug("Mock local WebSocket adapter - disconnect simulated (exchangeName: {}, connectionId: {})",
                exchangeName, currentConnectionId);
        eventPublisher.publishEvent(WebSocketDisconnectedEvent.manual(
                exchangeName,
                "Mock local disconnect",
                currentConnectionId
        ));
    }

    @Override
    public void sendMessage(String message) {
        log.debug("Mock local WebSocket adapter - sendMessage ignored (message length: {})",
                message != null ? message.length() : 0);
    }

    @Override
    public boolean isConnected() {
        return connected.get();
    }
}
