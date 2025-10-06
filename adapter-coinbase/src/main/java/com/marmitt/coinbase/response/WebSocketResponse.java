package com.marmitt.coinbase.response;

public record WebSocketResponse(
        String type,
        String message
) {
}