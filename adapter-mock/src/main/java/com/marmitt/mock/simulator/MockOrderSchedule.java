package com.marmitt.mock.simulator;

import java.util.List;

/**
 * Planned events of a simulated order (T52).
 *
 * @param reservationHeld the order passed validation, so its balance is reserved until a fill or a
 *                        failure settles it; a canceled order gives back the unexecuted part
 */
public record MockOrderSchedule(List<MockScheduledOrderEvent> events, boolean reservationHeld) {

    public MockOrderSchedule {
        events = List.copyOf(events);
    }
}
