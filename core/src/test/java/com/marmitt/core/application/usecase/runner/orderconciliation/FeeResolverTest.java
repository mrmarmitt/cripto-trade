package com.marmitt.core.application.usecase.runner.orderconciliation;

import com.marmitt.core.domain.Symbol;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FeeResolverTest {

    private final FeeResolver resolver = new FeeResolver();

    @Test
    void classifiesFeeByAssetAgainstTheTradedPair() {
        assertEquals(FeeResolver.Kind.BASE, resolver.resolve(event("0.000001", "BTC")).kind());
        assertEquals(FeeResolver.Kind.QUOTE, resolver.resolve(event("0.066", "USDT")).kind());
        assertEquals(FeeResolver.Kind.UNSUPPORTED, resolver.resolve(event("0.0001", "BNB")).kind());
    }

    @Test
    void assetMatchIsCaseInsensitive() {
        assertEquals(FeeResolver.Kind.BASE, resolver.resolve(event("0.000001", "btc")).kind());
    }

    @Test
    void feeWithoutAssetIsUnknown() {
        assertEquals(FeeResolver.Kind.UNKNOWN, resolver.resolve(event("0.066", null)).kind());
    }

    @Test
    void zeroOrMissingFeeIsNone() {
        assertEquals(FeeResolver.Kind.NONE, resolver.resolve(event("0", "BTC")).kind());
        assertEquals(FeeResolver.Kind.NONE, resolver.resolve(event(null, null)).kind());
    }

    private static OrderDataDto event(String fee, String asset) {
        return new OrderDataDto("1", "c", Symbol.of("BTCUSDT"), OrderDataDto.OrderSide.BUY,
                OrderDataDto.OrderType.LIMIT, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN,
                fee == null ? null : new BigDecimal(fee), OrderDataDto.OrderStatus.FILLED, null, Instant.now(), asset);
    }
}
