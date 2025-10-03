package com.marmitt.application.spring.handler;

import com.marmitt.core.dto.events.*;
import com.marmitt.core.ports.inbound.handler.*;
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

    @EventListener
    public void handleConnectionEstablished(WebSocketConnectedEvent event) {
        handleConnectionEstablishedPort.execute(event);
    }

    @EventListener
    public void handleConnectionFailed(WebSocketFailedEvent event) {
        handleConnectionFailedPort.execute(event);
    }

    @EventListener
    public void handleConnectionClosed(WebSocketClosedEvent event) {
        handleConnectionClosedPort.execute(event);
    }

    @EventListener
    public void handleConnectionClosing(WebSocketClosingEvent event) {
        handleConnectionClosingPort.execute(event);
    }

    @EventListener
    public void handleConnectionDisconnected(WebSocketDisconnectedEvent event) {
        handleConnectionDisconnectedPort.execute(event);
    }
}