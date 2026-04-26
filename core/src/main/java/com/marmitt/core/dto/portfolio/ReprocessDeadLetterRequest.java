package com.marmitt.core.dto.portfolio;

public record ReprocessDeadLetterRequest(
        String requestedBy,
        String resolutionNote
) {
}
