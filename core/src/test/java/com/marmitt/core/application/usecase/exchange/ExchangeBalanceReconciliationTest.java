package com.marmitt.core.application.usecase.exchange;

import com.marmitt.core.domain.portfolio.GlobalBalance;
import com.marmitt.core.domain.runner.Position;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.exchange.ExchangeBalanceReport;
import com.marmitt.core.dto.exchange.ExchangeBalanceReport.AssetBalance;
import com.marmitt.core.dto.websocket.data.AccountDataDto;
import com.marmitt.core.enums.ExchangeBalanceStatus;
import com.marmitt.core.enums.RunnerStatus;
import com.marmitt.core.enums.TransactionStatus;
import com.marmitt.core.ports.outbound.exchange.ExchangeAdapterDescriptor;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeAccountQueryPort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import com.marmitt.core.ports.outbound.repository.GlobalBalanceRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExchangeBalanceReconciliationTest {

    private final GlobalBalanceRepositoryPort balances = mock(GlobalBalanceRepositoryPort.class);
    private final StrategyRunnerRepositoryPort runners = mock(StrategyRunnerRepositoryPort.class);
    private final ExchangeAdapterRepositoryPort adapters = mock(ExchangeAdapterRepositoryPort.class);
    private final ExchangeAdapterDescriptor adapter = mock(ExchangeAdapterDescriptor.class);
    private final ExchangeAccountQueryPort accountQuery = mock(ExchangeAccountQueryPort.class);
    private final ExchangeBalanceReconciliation reconciliation =
            new ExchangeBalanceReconciliation(balances, runners, adapters, new BigDecimal("0.00000001"));

    private final List<StrategyRunner> allRunners = new ArrayList<>();
    private final Map<String, BigDecimal> free = new HashMap<>();
    private final Map<String, BigDecimal> locked = new HashMap<>();

    @BeforeEach
    void exchangeWithAccountQuery() {
        when(runners.findAll()).thenReturn(allRunners);
        when(runners.findByRunnerIdAndStatuses(any(), eq(TransactionStatus.IN_FLIGHT))).thenReturn(List.of());
        when(runners.findActivePositionsByPortfolioId(any())).thenReturn(List.of());
        when(adapters.findAdapter("BINANCE")).thenReturn(Optional.of(adapter));
        when(adapter.hasAccountQuery()).thenReturn(true);
        when(adapter.accountQuery()).thenReturn(accountQuery);
        when(accountQuery.queryAccountSnapshot()).thenAnswer(inv -> new AccountDataDto("acc", free, locked, Instant.now()));
    }

    @Test
    void severalPortfoliosBelowTheAccountAreOkWithTheRestUnallocated() {
        portfolio("1000", "0");
        portfolio("1000", "0");
        free.put("USDT", new BigDecimal("9917.41534"));

        ExchangeBalanceReport report = single();

        assertEquals(ExchangeBalanceStatus.OK, report.status());
        assertEquals(2, report.portfolios().size());
        AssetBalance usdt = asset(report, "USDT");
        assertEquals(0, new BigDecimal("2000").compareTo(usdt.localExpected()));
        assertEquals(0, new BigDecimal("7917.41534").compareTo(usdt.unallocated()));
    }

    @Test
    void sumOfPortfoliosAboveTheAccountIsDeficit() {
        portfolio("6000", "0");
        portfolio("6000", "0");
        free.put("USDT", new BigDecimal("10000"));

        ExchangeBalanceReport report = single();

        assertEquals(ExchangeBalanceStatus.DEFICIT, report.status());
        assertEquals(0, new BigDecimal("-2000").compareTo(asset(report, "USDT").signedDelta()));
        assertEquals(1, report.deficits().size());
    }

    @Test
    void openLotCostIsNotExpectedInTheQuoteCurrencyButItsQuantityIsExpectedInTheAsset() {
        // Rodada 2 da T53: lote de 0.001 BTC a 82.853; o custo segue em reserved ate a SELL casar.
        UUID portfolioId = portfolio("917.147", "82.853");
        Position lot = lot(allRunners.getFirst().getId(), "0.001", "82.853");
        when(runners.findActivePositionsByPortfolioId(portfolioId)).thenReturn(List.of(lot));
        free.put("USDT", new BigDecimal("917.147"));
        free.put("BTC", new BigDecimal("0.001"));

        ExchangeBalanceReport report = single();

        assertEquals(ExchangeBalanceStatus.OK, report.status(), "lote aberto com a conta coerente nao e deficit");
        assertEquals(0, new BigDecimal("917.147").compareTo(asset(report, "USDT").localExpected()));
        assertEquals(0, new BigDecimal("0.001").compareTo(asset(report, "BTC").localExpected()));
    }

    @Test
    void boughtAssetMissingFromTheAccountIsDeficit() {
        UUID portfolioId = portfolio("917.147", "82.853");
        Position lot = lot(allRunners.getFirst().getId(), "0.001", "82.853");
        when(runners.findActivePositionsByPortfolioId(portfolioId)).thenReturn(List.of(lot));
        free.put("USDT", new BigDecimal("917.147"));

        ExchangeBalanceReport report = single();

        assertEquals(ExchangeBalanceStatus.DEFICIT, report.status());
        assertEquals(ExchangeBalanceStatus.DEFICIT, asset(report, "BTC").status());
        assertEquals(ExchangeBalanceStatus.OK, asset(report, "USDT").status());
    }

    @Test
    void lockedBalanceCountsAsAccountBalance() {
        portfolio("900", "100");
        free.put("USDT", new BigDecimal("900"));
        locked.put("USDT", new BigDecimal("100"));

        assertEquals(ExchangeBalanceStatus.OK, single().status(), "reserva de BUY no livro aparece como locked");
    }

    @Test
    void shortfallCoveredByOrdersInFlightIsPendingNotDeficit() {
        // BUY preenchida na exchange durante a parada: a conta ja gastou, o ctrade ainda tem a reserva.
        portfolio("900", "100");
        Transaction buy = mock(Transaction.class);
        when(buy.isBuy()).thenReturn(true);
        when(buy.getTotal()).thenReturn(new BigDecimal("100"));
        UUID runnerId = allRunners.getFirst().getId();
        when(runners.findByRunnerIdAndStatuses(runnerId, TransactionStatus.IN_FLIGHT))
                .thenReturn(List.of(buy));
        free.put("USDT", new BigDecimal("900.5"));
        free.put("BTC", new BigDecimal("0.001"));

        ExchangeBalanceReport report = single();

        assertEquals(ExchangeBalanceStatus.PENDING, report.status());
        assertEquals(0, new BigDecimal("100").compareTo(asset(report, "USDT").inFlight()));

        free.put("USDT", new BigDecimal("850"));
        assertEquals(ExchangeBalanceStatus.DEFICIT, single().status(), "falta maior que as ordens em voo");
    }

    @Test
    void differenceWithinTheThresholdIsOk() {
        portfolio("1000", "0");
        free.put("USDT", new BigDecimal("999.99999999"));

        assertEquals(ExchangeBalanceStatus.OK, single().status());
    }

    @Test
    void exchangeWithoutAccountQueryIsSkippedAndQueryFailureIsFailed() {
        portfolio("1000", "0");
        when(adapter.hasAccountQuery()).thenReturn(false);
        assertEquals(ExchangeBalanceStatus.SKIPPED, single().status());

        when(adapter.hasAccountQuery()).thenReturn(true);
        when(accountQuery.queryAccountSnapshot()).thenThrow(new IllegalStateException("timeout"));
        ExchangeBalanceReport failed = single();
        assertEquals(ExchangeBalanceStatus.FAILED, failed.status());
        assertEquals("ACCOUNT_QUERY_FAILED", failed.code());
        assertTrue(failed.assets().isEmpty());
    }

    @Test
    void archivedRunnersDoNotBringTheirPortfolioIntoTheCheck() {
        UUID portfolioId = portfolio("5000", "0");
        StrategyRunner archived = allRunners.getFirst();
        when(archived.getStatus()).thenReturn(RunnerStatus.ARCHIVED);
        portfolio("1000", "0");
        free.put("USDT", new BigDecimal("1000"));

        ExchangeBalanceReport report = single();

        assertEquals(ExchangeBalanceStatus.OK, report.status());
        assertTrue(!report.portfolios().contains(portfolioId));
    }

    private ExchangeBalanceReport single() {
        List<ExchangeBalanceReport> reports = reconciliation.reconcileAll();
        assertEquals(1, reports.size());
        return reports.getFirst();
    }

    private static AssetBalance asset(ExchangeBalanceReport report, String asset) {
        return report.assets().stream().filter(a -> a.asset().equals(asset)).findFirst().orElseThrow();
    }

    /** Portfolio em USDT com um runner BTCUSDT na BINANCE. */
    private UUID portfolio(String available, String reserved) {
        UUID portfolioId = UUID.randomUUID();
        GlobalBalance balance = mock(GlobalBalance.class);
        when(balance.getBaseCurrency()).thenReturn("USDT");
        when(balance.getTotalBalance()).thenReturn(new BigDecimal(available).add(new BigDecimal(reserved)));
        when(balances.findByPortfolioId(portfolioId)).thenReturn(Optional.of(balance));

        StrategyRunner runner = mock(StrategyRunner.class);
        UUID runnerId = UUID.randomUUID();
        when(runner.getId()).thenReturn(runnerId);
        when(runner.getPortfolioId()).thenReturn(portfolioId);
        when(runner.getStatus()).thenReturn(RunnerStatus.HALTED);
        when(runner.getExchangeId()).thenReturn("BINANCE");
        when(runner.getSymbol()).thenReturn("BTCUSDT");
        allRunners.add(runner);
        return portfolioId;
    }

    private static Position lot(UUID runnerId, String quantity, String cost) {
        Position lot = mock(Position.class);
        when(lot.getRunnerId()).thenReturn(runnerId);
        when(lot.getSymbol()).thenReturn("BTCUSDT");
        when(lot.getQuantity()).thenReturn(new BigDecimal(quantity));
        when(lot.getCostBasis()).thenReturn(new BigDecimal(cost));
        return lot;
    }
}
