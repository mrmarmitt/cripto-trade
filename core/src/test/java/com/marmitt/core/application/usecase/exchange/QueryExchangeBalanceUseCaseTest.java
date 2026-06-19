package com.marmitt.core.application.usecase.exchange;

import com.marmitt.core.dto.exchange.BalanceDto;
import com.marmitt.core.dto.exchange.ExchangeBalanceSnapshot;
import com.marmitt.core.dto.websocket.data.AccountDataDto;
import com.marmitt.core.ports.outbound.exchange.ExchangeAdapterDescriptor;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeAccountQueryPort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QueryExchangeBalanceUseCaseTest {

    private static final String EXCHANGE = "BINANCE";
    private static final Instant AS_OF = Instant.parse("2026-01-01T10:00:00Z");

    private final ExchangeAdapterRepositoryPort repository = mock(ExchangeAdapterRepositoryPort.class);
    private final QueryExchangeBalanceUseCase useCase = new QueryExchangeBalanceUseCase(repository);

    @Test
    void queryBalances_throwsWhenExchangeNotRegistered() {
        when(repository.findAdapter("UNKNOWN")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> useCase.queryBalances("UNKNOWN"));
    }

    @Test
    void queryBalances_throwsUnsupportedWhenAccountQueryAbsent() {
        ExchangeAdapterDescriptor descriptor = mock(ExchangeAdapterDescriptor.class);
        when(descriptor.hasAccountQuery()).thenReturn(false);
        when(repository.findAdapter("COINBASE")).thenReturn(Optional.of(descriptor));

        UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class,
                () -> useCase.queryBalances("COINBASE"));
        assertTrue(ex.getMessage().contains("not available"));
    }

    @Test
    void queryBalances_mapsAccountSnapshotToBalanceSnapshot() {
        AccountDataDto account = new AccountDataDto(
                "acc-1",
                Map.of("USDT", new BigDecimal("100.50"), "BTC", new BigDecimal("0.20")),
                Map.of("USDT", new BigDecimal("25.00")),
                AS_OF);
        ExchangeAdapterDescriptor descriptor = descriptorWithAccount(account);
        when(repository.findAdapter(EXCHANGE)).thenReturn(Optional.of(descriptor));

        ExchangeBalanceSnapshot snapshot = useCase.queryBalances(EXCHANGE);

        assertEquals(EXCHANGE, snapshot.exchangeName());
        assertEquals(AS_OF, snapshot.retrievedAt());
        assertEquals(2, snapshot.balances().size());

        BalanceDto usdt = snapshot.balanceOf("usdt").orElseThrow();
        assertEquals(new BigDecimal("100.50"), usdt.free());
        assertEquals(new BigDecimal("25.00"), usdt.locked());
        assertEquals(new BigDecimal("125.50"), usdt.total());
    }

    @Test
    void queryBalances_reportsAssetPresentOnlyInLockedWithZeroFree() {
        AccountDataDto account = new AccountDataDto(
                "acc-1",
                Map.of("USDT", new BigDecimal("100")),
                Map.of("BTC", new BigDecimal("0.01")),
                AS_OF);
        ExchangeAdapterDescriptor descriptor = descriptorWithAccount(account);
        when(repository.findAdapter(EXCHANGE)).thenReturn(Optional.of(descriptor));

        ExchangeBalanceSnapshot snapshot = useCase.queryBalances(EXCHANGE);

        BalanceDto btc = snapshot.balanceOf("BTC").orElseThrow();
        assertEquals(BigDecimal.ZERO, btc.free());
        assertEquals(new BigDecimal("0.01"), btc.locked());
        assertEquals(new BigDecimal("0.01"), btc.total());
    }

    private static ExchangeAdapterDescriptor descriptorWithAccount(AccountDataDto account) {
        ExchangeAdapterDescriptor descriptor = mock(ExchangeAdapterDescriptor.class);
        ExchangeAccountQueryPort accountQuery = mock(ExchangeAccountQueryPort.class);
        when(descriptor.exchangeName()).thenReturn(EXCHANGE);
        when(descriptor.hasAccountQuery()).thenReturn(true);
        when(descriptor.accountQuery()).thenReturn(accountQuery);
        when(accountQuery.queryAccountSnapshot()).thenReturn(account);
        return descriptor;
    }
}
