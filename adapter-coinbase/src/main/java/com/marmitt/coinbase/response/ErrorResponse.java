package com.marmitt.coinbase.response;

public record ErrorResponse(
        String type,
        String message,
        String reason
) {
}