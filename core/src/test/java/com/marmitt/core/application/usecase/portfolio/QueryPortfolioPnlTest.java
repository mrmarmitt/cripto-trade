package com.marmitt.core.application.usecase.portfolio;

import com.marmitt.core.domain.portfolio.GlobalBalance;
import com.marmitt.core.domain.portfolio.Portfolio;
import com.marmitt.core.domain.runner.Position;
import com.marmitt.core.dto.portfolio.response.PortfolioPnlDto;
import com.marmitt.core.ports.outbound.marketdata.LatestPricePort;
import com.marmitt.core.ports.outbound.repository.GlobalBalanceRepositoryPort;
import com.marmitt.core.ports.outbound.repository.PortfolioRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * T58: o PnL diz quantos lotes ficaram fora do flutuante por falta de preco e de quando e o preco usado,
 * para "flutuante 0" nao ser lido como "lote no zero a zero".
 */
class QueryPortfolioPnlTest {

    private static final Instant TICK = Instant.parse("2026-10-21T11:20:00Z");

    private final PortfolioRepositoryPort portfolios = mock(PortfolioRepositoryPort.class);
    private final StrategyRunnerRepositoryPort runners = mock(StrategyRunnerRepositoryPort.class);
    private final GlobalBalanceRepositoryPort balances = mock(GlobalBalanceRepositoryPort.class);
    private final LatestPricePort prices = mock(LatestPricePort.class);
    private final QueryPortfolioUseCase useCase = new QueryPortfolioUseCase(portfolios, runners, balances, prices);

    private final UUID portfolioId = UUID.randomUUID();

    @BeforeEach
    void portfolioWithOneOpenLot() {
        Portfolio portfolio = mock(Portfolio.class);
        when(portfolios.findById(portfolioId)).thenReturn(Optional.of(portfolio));
        GlobalBalance balance = mock(GlobalBalance.class);
        when(balance.getRealizedBalance()).thenReturn(new BigDecimal("-0.01401"));
        when(balances.findByPortfolioId(portfolioId)).thenReturn(Optional.of(balance));
        Position lot = Position.open(UUID.randomUUID(), "BTCUSDT", new BigDecimal("0.001"), new BigDecimal("82.853"));
        when(runners.findActivePositionsByPortfolioId(portfolioId)).thenReturn(List.of(lot));
    }

    @Test
    void lotWithoutPriceIsCountedInsteadOfSilentlyZero() {
        when(prices.find("BTCUSDT")).thenReturn(Optional.empty());

        PortfolioPnlDto pnl = useCase.findPnlByPortfolioId(portfolioId).orElseThrow();

        assertEquals(1, pnl.unpricedPositions());
        assertEquals(0, BigDecimal.ZERO.compareTo(pnl.unrealizedPnL()), "parcial: sem o lote sem preco");
        assertNull(pnl.priceAsOf());
    }

    @Test
    void pricedLotReportsWhenThePriceIsFrom() {
        when(prices.find("BTCUSDT")).thenReturn(Optional.of(new BigDecimal("83853")));
        when(prices.findUpdatedAt("BTCUSDT")).thenReturn(Optional.of(TICK));

        PortfolioPnlDto pnl = useCase.findPnlByPortfolioId(portfolioId).orElseThrow();

        assertEquals(0, pnl.unpricedPositions());
        assertEquals(0, new BigDecimal("1").compareTo(pnl.unrealizedPnL()), "0.001 x 83853 - 82.853");
        assertEquals(TICK, pnl.priceAsOf());
    }
}
