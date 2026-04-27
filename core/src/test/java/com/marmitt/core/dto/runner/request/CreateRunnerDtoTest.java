package com.marmitt.core.dto.runner.request;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CreateRunnerDtoTest {

    @Test
    void rejectsBlankExchangeName() {
        assertThrows(IllegalArgumentException.class, () -> new CreateRunnerDto(
                UUID.randomUUID(),
                "BTCUSDT",
                "   ",
                null
        ));
    }

    @Test
    void acceptsValidInput() {
        assertDoesNotThrow(() -> new CreateRunnerDto(
                UUID.randomUUID(),
                "BTCUSDT",
                "BINANCE",
                null
        ));
    }
}
