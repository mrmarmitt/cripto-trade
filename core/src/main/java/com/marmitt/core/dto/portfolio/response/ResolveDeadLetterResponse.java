package com.marmitt.core.dto.portfolio.response;

import java.util.UUID;

public record ResolveDeadLetterResponse(
        UUID deadLetterId,
        boolean resolved,
        String message,
        DeadLetterEntryDto entry
) {
    public static ResolveDeadLetterResponse success(DeadLetterEntryDto entry, String message) {
        return new ResolveDeadLetterResponse(entry.id(), true, message, entry);
    }

    public static ResolveDeadLetterResponse failure(UUID deadLetterId, String message) {
        return new ResolveDeadLetterResponse(deadLetterId, false, message, null);
    }
}

