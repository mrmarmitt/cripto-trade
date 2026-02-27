package com.marmitt.mock.simulator;

public record OrderValidationResult(boolean accepted, String rejectReason) {

    public static OrderValidationResult ok() {
        return new OrderValidationResult(true, null);
    }

    public static OrderValidationResult reject(String reason) {
        return new OrderValidationResult(false, reason);
    }
}
