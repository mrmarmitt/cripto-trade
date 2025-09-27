package com.marmitt.controller.dto;

public record OrderNotificationSubscribeResponse(
        String message,
        boolean success
) {
    public static OrderNotificationSubscribeResponse error(String errorMessage) {
        return new OrderNotificationSubscribeResponse(errorMessage, false);
    }

    public static OrderNotificationSubscribeResponse successfully(String action) {
        return new OrderNotificationSubscribeResponse("Order notification " + action + " completed successfully", true);
    }
}