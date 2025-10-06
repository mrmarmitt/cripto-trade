package com.marmitt.application.spring.controller.dto.market;

public record MarketDataStreamResponse(
        String message,
        boolean success
) {
    public static MarketDataStreamResponse error(String errorMessage) {
        return new MarketDataStreamResponse(errorMessage, false);
    }

    public static MarketDataStreamResponse successfully(String action) {
        return new MarketDataStreamResponse("Market data " + action + " completed successfully", true);
    }
}