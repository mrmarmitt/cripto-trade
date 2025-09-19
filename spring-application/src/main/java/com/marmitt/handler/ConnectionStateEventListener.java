package com.marmitt.handler;

import com.marmitt.core.dto.events.WebSocketConnectedEvent;
import com.marmitt.core.dto.events.WebSocketFailedEvent;
import com.marmitt.core.dto.events.WebSocketClosedEvent;
import com.marmitt.core.dto.events.WebSocketClosingEvent;
import com.marmitt.core.dto.events.WebSocketDisconnectedEvent;
import com.marmitt.core.ports.inbound.websocket.HandleConnectionEstablishedPort;
import com.marmitt.core.ports.inbound.websocket.HandleConnectionFailedPort;
import com.marmitt.core.ports.inbound.websocket.HandleConnectionClosedPort;
import com.marmitt.core.ports.inbound.websocket.HandleConnectionClosingPort;
import com.marmitt.core.ports.inbound.websocket.HandleConnectionDisconnectedPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ConnectionStateEventListener {

    private final HandleConnectionEstablishedPort handleConnectionEstablishedPort;
    private final HandleConnectionFailedPort handleConnectionFailedPort;
    private final HandleConnectionClosedPort handleConnectionClosedPort;
    private final HandleConnectionClosingPort handleConnectionClosingPort;
    private final HandleConnectionDisconnectedPort handleConnectionDisconnectedPort;

    public ConnectionStateEventListener(
            HandleConnectionEstablishedPort handleConnectionEstablishedPort,
            HandleConnectionFailedPort handleConnectionFailedPort,
            HandleConnectionClosedPort handleConnectionClosedPort,
            HandleConnectionClosingPort handleConnectionClosingPort,
            HandleConnectionDisconnectedPort handleConnectionDisconnectedPort) {
        
        this.handleConnectionEstablishedPort = handleConnectionEstablishedPort;
        this.handleConnectionFailedPort = handleConnectionFailedPort;
        this.handleConnectionClosedPort = handleConnectionClosedPort;
        this.handleConnectionClosingPort = handleConnectionClosingPort;
        this.handleConnectionDisconnectedPort = handleConnectionDisconnectedPort;
    }

    /**
     * Delegates WebSocket connection established events to appropriate use case.
     */
    @EventListener
    public void handleConnectionEstablished(WebSocketConnectedEvent event) {
        log.debug("Delegating connection established event for exchange: {} to use case", event.exchange());
        handleConnectionEstablishedPort.execute(event);
    }

    /**
     * Delegates WebSocket connection failed events to appropriate use case.
     */
    @EventListener
    public void handleConnectionFailed(WebSocketFailedEvent event) {
        log.debug("Delegating connection failed event for exchange: {} to use case", event.exchange());
        handleConnectionFailedPort.execute(event);
    }

    /**
     * Delegates WebSocket connection closed events to appropriate use case.
     */
    @EventListener
    public void handleConnectionClosed(WebSocketClosedEvent event) {
        log.debug("Delegating connection closed event for exchange: {} to use case", event.exchange());
        handleConnectionClosedPort.execute(event);
    }

    /**
     * Delegates WebSocket connection closing events to appropriate use case.
     */
    @EventListener
    public void handleConnectionClosing(WebSocketClosingEvent event) {
        log.debug("Delegating connection closing event for exchange: {} to use case", event.exchange());
        handleConnectionClosingPort.execute(event);
    }

    /**
     * Delegates WebSocket disconnection events to appropriate use case.
     */
    @EventListener
    public void handleConnectionDisconnected(WebSocketDisconnectedEvent event) {
        log.debug("Delegating disconnection event for exchange: {} to use case", event.exchange());
        handleConnectionDisconnectedPort.execute(event);
    }
}