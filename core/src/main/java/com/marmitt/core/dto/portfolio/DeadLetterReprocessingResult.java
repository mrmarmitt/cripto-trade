package com.marmitt.core.dto.portfolio;

public record DeadLetterReprocessingResult(
        boolean applied,
        String message
) {
    public static DeadLetterReprocessingResult applied(String message) {
        return new DeadLetterReprocessingResult(true, message);
    }

    public static DeadLetterReprocessingResult notApplied(String message) {
        return new DeadLetterReprocessingResult(false, message);
    }
}
