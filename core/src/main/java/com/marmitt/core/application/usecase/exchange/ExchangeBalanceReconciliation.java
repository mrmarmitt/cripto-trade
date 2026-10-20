package com.marmitt.core.application.usecase.exchange;

import com.marmitt.core.domain.Symbol;
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
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import com.marmitt.core.ports.outbound.repository.GlobalBalanceRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Conferencia de saldo por exchange (T57): o que o ctrade acha que tem numa exchange nao pode passar do
 * que a conta tem. O portfolio e um orcamento virtual sobre uma conta compartilhada, entao a comparacao e
 * da <b>soma</b> dos portfolios da exchange contra a conta, nunca de um portfolio contra a conta inteira.
 *
 * <p>Por exchange (portfolios com runner nao arquivado nela):
 * <ul>
 *   <li><b>Moeda de cotacao</b> (moeda base do portfolio): {@code available + reserved - custo dos lotes
 *       abertos}. O custo da BUY executada fica em {@code reserved} ate a SELL casar
 *       ({@code GlobalBalance.confirmExecution}), mas na conta ja virou o ativo comprado; o que sobra e o
 *       disponivel mais as reservas de BUY em voo (na conta, {@code locked}).</li>
 *   <li><b>Ativo comprado</b>: soma da quantidade dos lotes abertos ({@code OPEN}/{@code CLOSING}); SELL no
 *       livro trava o ativo em {@code locked}.</li>
 * </ul>
 * Cada um contra {@code free + locked} da conta. Falta alem da tolerancia e {@code DEFICIT}, salvo quando
 * cabe nas ordens em voo ({@code PENDING}): fill na exchange ainda nao conciliado no ctrade (durante a
 * parada, antes da fase 3 do boot, ou no instante da leitura). Sobra e normal ({@code unallocated}).
 *
 * <p>Somente leitura. Quem reage (HALT) e o chamador: boot fase 2 e avaliacao periodica.
 */
@Slf4j
public class ExchangeBalanceReconciliation {

    private final GlobalBalanceRepositoryPort balanceRepository;
    private final StrategyRunnerRepositoryPort runnerRepository;
    private final ExchangeAdapterRepositoryPort exchangeAdapters;
    private final BigDecimal threshold;

    /**
     * @param threshold diferenca absoluta tolerada por moeda/ativo
     *                  ({@code runner.boot.phase2.portfolio.sanity-check.threshold})
     */
    public ExchangeBalanceReconciliation(GlobalBalanceRepositoryPort balanceRepository,
                                         StrategyRunnerRepositoryPort runnerRepository,
                                         ExchangeAdapterRepositoryPort exchangeAdapters,
                                         BigDecimal threshold) {
        if (threshold == null || threshold.signum() < 0) {
            throw new IllegalArgumentException("threshold must be zero or positive: " + threshold);
        }
        this.balanceRepository = balanceRepository;
        this.runnerRepository = runnerRepository;
        this.exchangeAdapters = exchangeAdapters;
        this.threshold = threshold;
    }

    /** Uma conferencia por exchange com runner nao arquivado, em ordem alfabetica. */
    public List<ExchangeBalanceReport> reconcileAll() {
        List<StrategyRunner> runners = runnerRepository.findAll();
        Set<String> exchanges = runners.stream()
                .filter(runner -> runner.getStatus() != RunnerStatus.ARCHIVED)
                .map(StrategyRunner::getExchangeId)
                .filter(exchange -> exchange != null && !exchange.isBlank())
                .map(String::toUpperCase)
                .collect(Collectors.toCollection(TreeSet::new));
        return exchanges.stream().map(exchange -> reconcile(exchange, runners)).toList();
    }

    private ExchangeBalanceReport reconcile(String exchange, List<StrategyRunner> runners) {
        List<StrategyRunner> onExchange = runners.stream()
                .filter(runner -> exchange.equalsIgnoreCase(runner.getExchangeId()))
                .toList();
        List<UUID> portfolios = onExchange.stream()
                .filter(runner -> runner.getStatus() != RunnerStatus.ARCHIVED)
                .map(StrategyRunner::getPortfolioId)
                .distinct()
                .toList();

        Optional<ExchangeAdapterDescriptor> adapter = exchangeAdapters.findAdapter(exchange);
        if (adapter.isEmpty() || !adapter.get().hasAccountQuery()) {
            return ExchangeBalanceReport.notChecked(exchange, ExchangeBalanceStatus.SKIPPED,
                    "ACCOUNT_QUERY_NOT_AVAILABLE", "exchange account query capability is not available", portfolios);
        }

        try {
            Map<UUID, String> quoteByPortfolio = new LinkedHashMap<>();
            for (UUID portfolioId : portfolios) {
                Optional<String> quote = balanceRepository.findByPortfolioId(portfolioId)
                        .map(balance -> balance.getBaseCurrency().toUpperCase());
                if (quote.isEmpty()) {
                    return ExchangeBalanceReport.notChecked(exchange, ExchangeBalanceStatus.FAILED,
                            "GLOBAL_BALANCE_NOT_FOUND", "no balance for portfolio " + portfolioId, portfolios);
                }
                quoteByPortfolio.put(portfolioId, quote.get());
            }

            // Ordens em voo antes do saldo local e da conta: um fill que aconteca entre as leituras fica
            // coberto pela folga (PENDING), em vez de virar falta.
            Map<String, BigDecimal> inFlight = new TreeMap<>();
            for (StrategyRunner runner : onExchange) {
                String quote = quoteByPortfolio.get(runner.getPortfolioId());
                if (quote == null) {
                    continue;
                }
                for (Transaction tx : runnerRepository.findByRunnerIdAndStatuses(runner.getId(),
                        TransactionStatus.IN_FLIGHT)) {
                    if (tx.isBuy()) {
                        add(inFlight, quote, tx.getTotal());
                    } else {
                        add(inFlight, baseAsset(tx.getSymbol()), tx.getQuantity());
                    }
                }
            }

            Set<UUID> runnerIds = onExchange.stream().map(StrategyRunner::getId).collect(Collectors.toSet());
            // Saldo local relido depois das ordens em voo, pelo mesmo motivo.
            Map<String, BigDecimal> expected = new TreeMap<>();
            for (Map.Entry<UUID, String> entry : quoteByPortfolio.entrySet()) {
                String quote = entry.getValue();
                add(expected, quote, balanceRepository.findByPortfolioId(entry.getKey())
                        .map(GlobalBalance::getTotalBalance).orElse(BigDecimal.ZERO));
                for (Position lot : runnerRepository.findActivePositionsByPortfolioId(entry.getKey())) {
                    if (runnerIds.contains(lot.getRunnerId())) {
                        add(expected, quote, lot.getCostBasis().negate());
                        add(expected, baseAsset(lot.getSymbol()), lot.getQuantity());
                    }
                }
            }

            AccountDataDto account = adapter.get().accountQuery().queryAccountSnapshot();
            List<AssetBalance> assets = new ArrayList<>();
            for (Map.Entry<String, BigDecimal> entry : expected.entrySet()) {
                assets.add(compare(entry.getKey(), entry.getValue(), account,
                        inFlight.getOrDefault(entry.getKey(), BigDecimal.ZERO)));
            }
            return ExchangeBalanceReport.checked(exchange, portfolios, assets);
        } catch (UnsupportedOperationException e) {
            return ExchangeBalanceReport.notChecked(exchange, ExchangeBalanceStatus.SKIPPED,
                    "ACCOUNT_QUERY_UNSUPPORTED", e.getMessage(), portfolios);
        } catch (RuntimeException e) {
            log.warn("exchangeBalance: check failed exchange={} reason={}", exchange, e.getMessage());
            return ExchangeBalanceReport.notChecked(exchange, ExchangeBalanceStatus.FAILED,
                    "ACCOUNT_QUERY_FAILED", e.getMessage(), portfolios);
        }
    }

    private AssetBalance compare(String asset, BigDecimal localExpected, AccountDataDto account, BigDecimal inFlight) {
        BigDecimal exchangeTotal = amount(account.balances(), asset).add(amount(account.lockedBalances(), asset));
        BigDecimal signedDelta = exchangeTotal.subtract(localExpected);
        BigDecimal shortfall = signedDelta.negate();
        ExchangeBalanceStatus status;
        if (shortfall.compareTo(threshold) <= 0) {
            status = ExchangeBalanceStatus.OK;
        } else if (shortfall.compareTo(inFlight.add(threshold)) <= 0) {
            status = ExchangeBalanceStatus.PENDING;
        } else {
            status = ExchangeBalanceStatus.DEFICIT;
        }
        return new AssetBalance(asset, localExpected, exchangeTotal, signedDelta,
                signedDelta.max(BigDecimal.ZERO), inFlight, status);
    }

    private static BigDecimal amount(Map<String, BigDecimal> balances, String asset) {
        return balances == null ? BigDecimal.ZERO : balances.getOrDefault(asset, BigDecimal.ZERO);
    }

    private static String baseAsset(String symbol) {
        return Symbol.of(symbol).getBaseAsset().toUpperCase();
    }

    private static void add(Map<String, BigDecimal> totals, String asset, BigDecimal amount) {
        totals.merge(asset, amount, BigDecimal::add);
    }
}
