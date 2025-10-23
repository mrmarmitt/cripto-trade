package com.marmitt.application.spring.handler;

import com.marmitt.core.dto.events.*;
import com.marmitt.core.ports.inbound.handler.*;
import com.marmitt.core.ports.inbound.websocket.PostConnectionEstablishedPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ConnectionStateEventListener {

    private final ConnectionEstablishedPort handleConnectionEstablishedPort;
    private final PostConnectionEstablishedPort handlePostConnectionEstablishedPort;
    private final ConnectionFailedPort handleConnectionFailedPort;
    private final ConnectionClosedPort handleConnectionClosedPort;
    private final ConnectionClosingPort handleConnectionClosingPort;
    private final ConnectionDisconnectedPort handleConnectionDisconnectedPort;

    public ConnectionStateEventListener(
            ConnectionEstablishedPort handleConnectionEstablishedPort,
            PostConnectionEstablishedPort handlePostConnectionEstablishedPort,
            ConnectionFailedPort handleConnectionFailedPort,
            ConnectionClosedPort handleConnectionClosedPort,
            ConnectionClosingPort handleConnectionClosingPort,
            ConnectionDisconnectedPort handleConnectionDisconnectedPort) {
        
        this.handleConnectionEstablishedPort = handleConnectionEstablishedPort;
        this.handlePostConnectionEstablishedPort = handlePostConnectionEstablishedPort;
        this.handleConnectionFailedPort = handleConnectionFailedPort;
        this.handleConnectionClosedPort = handleConnectionClosedPort;
        this.handleConnectionClosingPort = handleConnectionClosingPort;
        this.handleConnectionDisconnectedPort = handleConnectionDisconnectedPort;
    }

    @EventListener
    public void handleConnectionEstablished(WebSocketConnectedEvent event) {
        handleConnectionEstablishedPort.execute(event);
        handlePostConnectionEstablishedPort.execute(event);
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