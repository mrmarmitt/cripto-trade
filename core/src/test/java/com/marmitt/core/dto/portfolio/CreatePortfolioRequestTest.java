package com.marmitt.core.dto.portfolio.request;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CreatePortfolioRequestTest {

    @Test
    void rejectsBlankCurrency() {
        assertThrows(IllegalArgumentException.class, () -> new CreatePortfolioRequest(
                "Main",
                new BigDecimal("1000"),
                "   "
        ));
    }

    @Test
    void acceptsValidCurrency() {
        assertDoesNotThrow(() -> new CreatePortfolioRequest(
                "Main",
                new BigDecimal("1000"),
                "USDT"
        ));
    }
}

