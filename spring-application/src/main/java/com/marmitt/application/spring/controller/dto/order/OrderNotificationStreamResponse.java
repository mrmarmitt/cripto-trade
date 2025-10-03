package com.marmitt.application.spring.controller.dto.order;

public record OrderNotificationStreamResponse(
        String message,
        boolean success
) {
    public static OrderNotificationStreamResponse error(String errorMessage) {
        return new OrderNotificationStreamResponse(errorMessage, false);
    }

    public static OrderNotificationStreamResponse successfully(String action) {
        return new OrderNotificationStreamResponse("Order notification " + action + " completed successfully", true);
    }
}