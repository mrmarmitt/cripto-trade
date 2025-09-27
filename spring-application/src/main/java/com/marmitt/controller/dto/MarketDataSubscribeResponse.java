package com.marmitt.controller.dto;

public record MarketDataSubscribeResponse(
        String message,
        boolean success
) {
    public static MarketDataSubscribeResponse error(String errorMessage) {
        return new MarketDataSubscribeResponse(errorMessage, false);
    }

    public static MarketDataSubscribeResponse successfully(String action) {
        return new MarketDataSubscribeResponse("Market data " + action + " completed successfully", true);
    }
}