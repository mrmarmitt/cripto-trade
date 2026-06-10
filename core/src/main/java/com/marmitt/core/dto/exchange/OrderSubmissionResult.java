package com.marmitt.core.dto.exchange;

import com.marmitt.core.dto.websocket.data.OrderDataDto;

public record OrderSubmissionResult(
        boolean asyncDispatched,
        OrderDataDto syncResult,
        String failureReason
) {

    public static OrderSubmissionResult dispatched() {
        return new OrderSubmissionResult(true, null, null);
    }

    public static OrderSubmissionResult completed(OrderDataDto dto) {
        return new OrderSubmissionResult(false, dto, null);
    }

    public static OrderSubmissionResult failed(String reason) {
        return new OrderSubmissionResult(false, null, reason);
    }

    public boolean isCompleted() {
        return syncResult != null;
    }

    public boolean isFailed() {
        return failureReason != null;
    }
}
