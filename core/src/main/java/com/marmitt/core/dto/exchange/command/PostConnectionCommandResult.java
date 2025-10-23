package com.marmitt.core.dto.exchange.command;

import java.time.Instant;
import java.util.Optional;

public record PostConnectionCommandResult(
        String exchangeName,
        boolean success,
        Optional<String> message,
        Optional<String> errorMessage,
        Instant executedAt
) {
    
    public static PostConnectionCommandResult success(String exchangeName, 
                                                     String message) {
        return new PostConnectionCommandResult(
                exchangeName,
                true,
                Optional.of(message),
                Optional.empty(),
                Instant.now()
        );
    }
    
    public static PostConnectionCommandResult failure(String exchangeName, 
                                                     String errorMessage) {
        return new PostConnectionCommandResult(
                exchangeName,
                false,
                Optional.empty(),
                Optional.of(errorMessage),
                Instant.now()
        );
    }
}