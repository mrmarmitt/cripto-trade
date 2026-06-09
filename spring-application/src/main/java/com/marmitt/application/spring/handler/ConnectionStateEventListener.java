package com.marmitt.application.spring.handler;

import com.marmitt.core.application.handler.connection.ConnectionLostOrderDispatchGuard;
import com.marmitt.core.dto.events.*;
import com.marmitt.core.dto.exchange.command.PostConnectionCommandResult;
import com.marmitt.core.enums.StreamChannel;
import com.marmitt.core.ports.inbound.handler.*;
import com.marmitt.core.ports.inbound.websocket.PostConnectionEstablishedPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ConnectionStateEventListener {

    private final ConnectionEstablishedPort handleConnectionEstablishedPort;
    private final PostConnectionEstablishedPort handlePostConnectionEstablishedPort;
    private final ConnectionFailedPort handleConnectionFailedPort;
    private final CriticalConnectionFailedPort handleCriticalConnectionFailedPort;
    private final ConnectionClosedPort handleConnectionClosedPort;
    private final ConnectionClosingPort handleConnectionClosingPort;
    private final ConnectionDisconnectedPort handleConnectionDisconnectedPort;
    private final ConnectionLostOrderDispatchGuard dispatchGuard;
    private final ApplicationEventPublisher eventPublisher;

    public ConnectionStateEventListener(
            ConnectionEstablishedPort handleConnectionEstablishedPort,
            PostConnectionEstablishedPort handlePostConnectionEstablishedPort,
            ConnectionFailedPort handleConnectionFailedPort,
            CriticalConnectionFailedPort handleCriticalConnectionFailedPort,
            ConnectionClosedPort handleConnectionClosedPort,
            ConnectionClosingPort handleConnectionClosingPort,
            ConnectionDisconnectedPort handleConnectionDisconnectedPort,
            ConnectionLostOrderDispatchGuard dispatchGuard,
            ApplicationEventPublisher eventPublisher) {

        this.handleConnectionEstablishedPort = handleConnectionEstablishedPort;
        this.handlePostConnectionEstablishedPort = handlePostConnectionEstablishedPort;
        this.handleConnectionFailedPort = handleConnectionFailedPort;
        this.handleCriticalConnectionFailedPort = handleCriticalConnectionFailedPort;
        this.handleConnectionClosedPort = handleConnectionClosedPort;
        this.handleConnectionClosingPort = handleConnectionClosingPort;
        this.handleConnectionDisconnectedPort = handleConnectionDisconnectedPort;
        this.dispatchGuard = dispatchGuard;
        this.eventPublisher = eventPublisher;
    }

    @EventListener
    public void handleConnectionEstablished(WebSocketConnectedEvent event) {
        handleConnectionEstablishedPort.execute(event);
        PostConnectionCommandResult result = handlePostConnectionEstablishedPort.execute(event);
        if (!result.success() && event.channel() == StreamChannel.USER_DATA) {
            log.warn("Post-connection setup failed for USER_DATA — triggering reconnect: exchange={} error={}",
                    event.exchange(), result.errorMessage().orElse("unknown"));
            eventPublisher.publishEvent(WebSocketFailedEvent.of(
                    event.exchange(),
                    result.errorMessage().orElse("Post-connection setup failed"),
                    event.connectionId(),
                    null,
                    event.channel()));
        } else {
            dispatchGuard.onConnectionReestablished(event);
        }
    }

    @EventListener
    public void handleConnectionFailed(WebSocketFailedEvent event) {
        dispatchGuard.onConnectionFailed(event);
        if (event.isCritical()) {
            handleCriticalConnectionFailedPort.execute(event);
            return;
        }
        handleConnectionFailedPort.execute(event);
    }

    @EventListener
    public void handleConnectionClosed(WebSocketClosedEvent event) {
        handleConnectionClosedPort.execute(event);
        dispatchGuard.onConnectionClosed(event);
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
