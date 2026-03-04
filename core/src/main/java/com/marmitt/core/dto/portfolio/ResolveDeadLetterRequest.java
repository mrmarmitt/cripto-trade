package com.marmitt.core.dto.portfolio;

public record ResolveDeadLetterRequest(
        String resolvedBy,
        String resolutionNote
) {
}
