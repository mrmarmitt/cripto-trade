package com.marmitt.application.spring.handler;

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
    private final ConnectionClosedPort handleConnectionClosedPort;
    private final ConnectionClosingPort handleConnectionClosingPort;
    private final ConnectionDisconnectedPort handleConnectionDisconnectedPort;
    private final ApplicationEventPublisher eventPublisher;

    public ConnectionStateEventListener(
            ConnectionEstablishedPort handleConnectionEstablishedPort,
            PostConnectionEstablishedPort handlePostConnectionEstablishedPort,
            ConnectionFailedPort handleConnectionFailedPort,
            ConnectionClosedPort handleConnectionClosedPort,
            ConnectionClosingPort handleConnectionClosingPort,
            ConnectionDisconnectedPort handleConnectionDisconnectedPort,
            ApplicationEventPublisher eventPublisher) {

        this.handleConnectionEstablishedPort = handleConnectionEstablishedPort;
        this.handlePostConnectionEstablishedPort = handlePostConnectionEstablishedPort;
        this.handleConnectionFailedPort = handleConnectionFailedPort;
        this.handleConnectionClosedPort = handleConnectionClosedPort;
        this.handleConnectionClosingPort = handleConnectionClosingPort;
        this.handleConnectionDisconnectedPort = handleConnectionDisconnectedPort;
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
        }
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