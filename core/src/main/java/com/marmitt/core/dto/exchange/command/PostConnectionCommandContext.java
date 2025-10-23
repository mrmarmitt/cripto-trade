package com.marmitt.core.dto.exchange.command;

import com.marmitt.core.dto.websocket.request.StreamSubscriptionRequest;

import java.util.UUID;

public record PostConnectionCommandContext(
        String exchangeName,
        UUID connectionId,
        StreamSubscriptionRequest originalRequest
) {
    
    public static PostConnectionCommandContext of(String exchangeName, 
                                                 UUID connectionId,
                                                  StreamSubscriptionRequest originalRequest) {
        return new PostConnectionCommandContext(exchangeName, connectionId, originalRequest);
    }
}