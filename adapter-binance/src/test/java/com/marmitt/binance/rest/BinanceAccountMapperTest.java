package com.marmitt.binance.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.core.dto.websocket.data.AccountDataDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class BinanceAccountMapperTest {

    private final BinanceAccountMapper mapper = new BinanceAccountMapper(new ObjectMapper());

    @Test
    void fromJson_shouldMapNonZeroBalances() {
        String json = """
                {
                  "updateTime": 1700000000000,
                  "balances": [
                    { "asset": "BTC",  "free": "0.5",      "locked": "0.0"   },
                    { "asset": "USDT", "free": "10000.00", "locked": "500.00" },
                    { "asset": "ETH",  "free": "0.0",      "locked": "0.0"   }
                  ]
                }
                """;

        AccountDataDto dto = mapper.fromJson(json);

        assertNotNull(dto);
        assertEquals(2, dto.balances().size(), "ETH with zero balance should be filtered out");
        assertTrue(dto.balances().containsKey("BTC"));
        assertTrue(dto.balances().containsKey("USDT"));
        assertFalse(dto.balances().containsKey("ETH"));

        assertEquals(0, dto.balances().get("BTC").compareTo(new BigDecimal("0.5")));
        assertEquals(0, dto.balances().get("USDT").compareTo(new BigDecimal("10000.00")));
        assertEquals(0, dto.lockedBalances().get("USDT").compareTo(new BigDecimal("500.00")));
        assertNotNull(dto.lastUpdateTime());
    }

    @Test
    void fromJson_shouldReturnEmptyMaps_whenAllBalancesAreZero() {
        String json = """
                {
                  "updateTime": 1700000000000,
                  "balances": [
                    { "asset": "BTC", "free": "0.0", "locked": "0.0" }
                  ]
                }
                """;

        AccountDataDto dto = mapper.fromJson(json);

        assertTrue(dto.balances().isEmpty());
        assertTrue(dto.lockedBalances().isEmpty());
    }

    @Test
    void fromJson_shouldInclude_whenOnlyLockedIsNonZero() {
        String json = """
                {
                  "updateTime": 1700000000000,
                  "balances": [
                    { "asset": "BNB", "free": "0.0", "locked": "1.5" }
                  ]
                }
                """;

        AccountDataDto dto = mapper.fromJson(json);

        assertTrue(dto.balances().containsKey("BNB"));
        assertEquals(0, dto.balances().get("BNB").compareTo(BigDecimal.ZERO));
        assertEquals(0, dto.lockedBalances().get("BNB").compareTo(new BigDecimal("1.5")));
    }
}
