package com.marmitt.core.ports.inbound.websocket;

import com.marmitt.core.dto.events.WebSocketConnectedEvent;
import com.marmitt.core.dto.exchange.command.PostConnectionCommandContext;
import com.marmitt.core.dto.exchange.command.PostConnectionCommandResult;

public interface PostConnectionEstablishedPort {

    PostConnectionCommandResult execute(WebSocketConnectedEvent event);
}