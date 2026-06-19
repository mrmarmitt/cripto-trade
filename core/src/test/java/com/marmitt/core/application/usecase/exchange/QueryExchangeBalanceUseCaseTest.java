package com.marmitt.core.application.usecase.exchange;

import com.marmitt.core.ports.outbound.exchange.ExchangeAdapterDescriptor;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QueryExchangeBalanceUseCaseTest {

    private static final String EXCHANGE = "BINANCE";

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

    // T28 é fundação: com a capacidade presente, o caminho de saldo ainda não está
    // implementado (mapeamento entregue em T29) e sinaliza via UnsupportedOperationException.
    @Test
    void queryBalances_dispatchesToAdapterButSignalsPendingImplementation() {
        ExchangeAdapterDescriptor descriptor = mock(ExchangeAdapterDescriptor.class);
        when(descriptor.hasAccountQuery()).thenReturn(true);
        when(repository.findAdapter(EXCHANGE)).thenReturn(Optional.of(descriptor));

        UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class,
                () -> useCase.queryBalances(EXCHANGE));
        assertTrue(ex.getMessage().contains("not implemented yet"));
    }
}
