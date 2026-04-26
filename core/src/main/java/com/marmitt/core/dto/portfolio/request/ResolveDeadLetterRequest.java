package com.marmitt.core.dto.portfolio.request;

public record ResolveDeadLetterRequest(
        String resolvedBy,
        String resolutionNote
) {
}

