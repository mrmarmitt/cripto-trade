package com.marmitt.mock.config;

import com.marmitt.core.dto.websocket.data.OrderDataDto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic, per-order override for mock order execution.
 *
 * <p>Used mainly by integration tests to force specific lifecycle sequences
 * without relying on random mock behavior.
 */
public record MockOrderScenarioOverride(
        List<PlannedEvent> events,
        EventOrdering ordering
) {

    public MockOrderScenarioOverride {
        Objects.requireNonNull(events, "events cannot be null");
        if (events.isEmpty()) {
            throw new IllegalArgumentException("events cannot be empty");
        }
        for (PlannedEvent event : events) {
            Objects.requireNonNull(event, "planned event cannot be null");
        }
        ordering = ordering == null ? EventOrdering.AS_IS : ordering;
    }

    public enum EventOrdering {
        AS_IS,
        REVERSE
    }

    /**
     * Planned event for one order update.
     *
     * @param status           order status to emit.
     * @param executedQuantity cumulative executed quantity (nullable; defaults by status).
     * @param executedPrice    executed price (nullable -> request price).
     * @param fee              fee for this event (nullable -> computed from increment for fill events, ZERO otherwise).
     * @param rejectReason     reject reason for REJECTED events.
     * @param delayBeforeMs    delay before publishing this event.
     * @param duplicates       number of duplicated emissions for this same payload.
     */
    public record PlannedEvent(
            OrderDataDto.OrderStatus status,
            BigDecimal executedQuantity,
            BigDecimal executedPrice,
            BigDecimal fee,
            String rejectReason,
            long delayBeforeMs,
            int duplicates
    ) {
        public PlannedEvent {
            Objects.requireNonNull(status, "status cannot be null");
            if (delayBeforeMs < 0) {
                throw new IllegalArgumentException("delayBeforeMs cannot be negative");
            }
            if (duplicates < 0) {
                throw new IllegalArgumentException("duplicates cannot be negative");
            }
        }
    }
}
