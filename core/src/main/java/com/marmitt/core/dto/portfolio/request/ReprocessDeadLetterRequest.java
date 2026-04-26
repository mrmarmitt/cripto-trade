package com.marmitt.core.dto.portfolio.request;

public record ReprocessDeadLetterRequest(
        String requestedBy,
        String resolutionNote
) {
}

