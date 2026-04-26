package com.marmitt.core.dto.portfolio.response;

import java.util.UUID;

public record ReprocessDeadLetterResponse(
        UUID deadLetterId,
        boolean reprocessed,
        String message,
        DeadLetterEntryDto entry
) {
    public static ReprocessDeadLetterResponse success(DeadLetterEntryDto entry, String message) {
        return new ReprocessDeadLetterResponse(entry.id(), true, message, entry);
    }

    public static ReprocessDeadLetterResponse failure(UUID deadLetterId, String message) {
        return new ReprocessDeadLetterResponse(deadLetterId, false, message, null);
    }
}

