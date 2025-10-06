package com.marmitt.application.spring.controller.dto.order;

public record OrderManagementResponse(
        String message,
        boolean success,
        String orderId
) {
    public static OrderManagementResponse error(String errorMessage) {
        return new OrderManagementResponse(errorMessage, false, null);
    }

    public static OrderManagementResponse successfully(String action, String orderId) {
        return new OrderManagementResponse("Order " + action + " completed successfully", true, orderId);
    }

    public static OrderManagementResponse successfully(String action) {
        return successfully(action, null);
    }
}