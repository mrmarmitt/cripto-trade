package com.marmitt.mock.simulator;

import com.marmitt.core.dto.websocket.data.OrderDataDto;

/**
 * Event wrapper with explicit delay control for deterministic mock scenarios.
 */
public record MockScheduledOrderEvent(
        OrderDataDto orderData,
        long delayBeforeMs
) {
}
