package com.marmitt.mock.simulator;

import com.marmitt.core.dto.websocket.data.OrderDataDto;

/**
 * Event wrapper with explicit delay control for deterministic mock scenarios.
 *
 * <p>{@code settlement} is the balance effect of the event (fill debit/credit, reservation release).
 * It runs when the event is published, not when the schedule is built, so an order canceled midway
 * only settles what was actually executed (T52). Duplicated copies of an event carry no settlement.
 */
public record MockScheduledOrderEvent(
        OrderDataDto orderData,
        long delayBeforeMs,
        Runnable settlement
) {
    private static final Runnable NO_SETTLEMENT = () -> { };

    public MockScheduledOrderEvent {
        settlement = settlement != null ? settlement : NO_SETTLEMENT;
    }

    public MockScheduledOrderEvent(OrderDataDto orderData, long delayBeforeMs) {
        this(orderData, delayBeforeMs, NO_SETTLEMENT);
    }

    /** Same event and delay, without the balance effect: for duplicated deliveries. */
    public MockScheduledOrderEvent duplicate(long delay) {
        return new MockScheduledOrderEvent(orderData, delay, NO_SETTLEMENT);
    }
}
