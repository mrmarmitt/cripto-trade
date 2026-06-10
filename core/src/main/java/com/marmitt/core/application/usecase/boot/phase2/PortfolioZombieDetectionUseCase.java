package com.marmitt.core.application.usecase.boot.phase2;

import com.marmitt.core.domain.Symbol;
import com.marmitt.core.domain.runner.ClientOrderId;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.portfolio.PortfolioZombieCandidate;
import com.marmitt.core.dto.portfolio.PortfolioZombieDetectionResult;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.enums.DlqReason;
import com.marmitt.core.enums.RunnerStatus;
import com.marmitt.core.ports.outbound.exchange.ExchangeAdapterDescriptor;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeOrderQueryPort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class PortfolioZombieDetectionUseCase {

    private static final int MAX_LOG_SAMPLES = 10;

    private final StrategyRunnerRepositoryPort strategyRunnerRepository;
    private final ExchangeAdapterRepositoryPort exchangeAdapterRepository;

    public PortfolioZombieDetectionUseCase(StrategyRunnerRepositoryPort strategyRunnerRepository,
                                           ExchangeAdapterRepositoryPort exchangeAdapterRepository) {
        this.strategyRunnerRepository = strategyRunnerRepository;
        this.exchangeAdapterRepository = exchangeAdapterRepository;
    }

    public PortfolioZombieDetectionResult execute(UUID portfolioId, String exchangeId) {
        return execute(portfolioId, exchangeId, true);
    }

    public PortfolioZombieDetectionResult execute(UUID portfolioId, String exchangeId, boolean cutoffEnabled) {
        List<StrategyRunner> scopedRunners = strategyRunnerRepository.findByPortfolioId(portfolioId).stream()
                .filter(runner -> runner.getStatus() != RunnerStatus.ARCHIVED)
                .filter(runner -> runner.getExchangeId() != null
                        && exchangeId != null
                        && exchangeId.equalsIgnoreCase(runner.getExchangeId()))
                .toList();

        if (scopedRunners.isEmpty()) {
            return PortfolioZombieDetectionResult.skipped(
                    portfolioId,
                    exchangeId,
                    "NO_RUNNERS",
                    "No eligible runners found for portfolio/exchange."
            );
        }

        Optional<ExchangeAdapterDescriptor> adapterOpt = exchangeAdapterRepository.findAdapter(exchangeId);
        if (adapterOpt.isEmpty() || !adapterOpt.get().hasOrderQuery()) {
            return PortfolioZombieDetectionResult.skipped(
                    portfolioId, exchangeId, "ORDER_QUERY_NOT_AVAILABLE",
                    "Exchange order query capability is not available.");
        }

        try {
            List<OrderDataDto> openOrders = adapterOpt.get().orderQuery().listAllOpenOrders();
            return classifyOpenOrders(
                    portfolioId,
                    exchangeId,
                    openOrders != null ? openOrders : List.of(),
                    scopedRunners,
                    cutoffEnabled
            );
        } catch (UnsupportedOperationException e) {
            return PortfolioZombieDetectionResult.skipped(
                    portfolioId, exchangeId, "ORDER_QUERY_UNSUPPORTED", e.getMessage());
        } catch (Exception e) {
            log.warn("portfolioZombieDetection: failed portfolioId={} exchange={} reason={}",
                    portfolioId, exchangeId, e.getMessage());
            return PortfolioZombieDetectionResult.failed(
                    portfolioId, exchangeId, "ORDER_QUERY_FAILED", e.getMessage());
        }
    }

    private PortfolioZombieDetectionResult classifyOpenOrders(UUID portfolioId,
                                                              String exchangeId,
                                                              List<OrderDataDto> openOrders,
                                                              List<StrategyRunner> scopedRunners,
                                                              boolean cutoffEnabled) {
        Map<UUID, StrategyRunner> scopedRunnerById = scopedRunners.stream()
                .collect(Collectors.toMap(StrategyRunner::getId, Function.identity()));
        Map<String, StrategyRunner> scopedRunnerByShortCode = scopedRunners.stream()
                .collect(Collectors.toMap(
                        runner -> runner.getShortCode().toLowerCase(Locale.ROOT),
                        Function.identity(),
                        (left, right) -> left
                ));
        Set<UUID> scopedRunnerIds = scopedRunnerById.keySet();

        int invalidFormatCount = 0;
        int unknownRunnerCount = 0;
        int noLocalMatchCount = 0;
        int beforeCutoffCount = 0;
        int unknownSymbolCount = 0;
        List<PortfolioZombieCandidate> samples = new ArrayList<>();
        Instant portfolioCutoff = cutoffEnabled ? resolvePortfolioCutoff(scopedRunners) : null;

        for (OrderDataDto order : openOrders) {
            String clientOrderId = order.clientOrderId();
            Optional<Transaction> transactionOptional = (clientOrderId == null || clientOrderId.isBlank())
                    ? Optional.empty()
                    : strategyRunnerRepository.findTransactionByClientOrderId(clientOrderId);
            String shortCode = ClientOrderId.getRunnerShortCode(clientOrderId);
            if (shortCode != null) {
                shortCode = shortCode.toLowerCase(Locale.ROOT);
            }
            final String normalizedShortCode = shortCode;

            boolean belongsToCurrentPortfolio = transactionOptional
                    .map(tx -> scopedRunnerIds.contains(tx.getRunnerId()))
                    .orElseGet(() -> normalizedShortCode != null
                            && scopedRunnerByShortCode.containsKey(normalizedShortCode));

            if (!belongsToCurrentPortfolio) {
                continue;
            }

            if (cutoffEnabled && isBeforeCutoff(order.timestamp(), portfolioCutoff)) {
                beforeCutoffCount++;
                addSample(samples, order, DlqReason.RECONCILIATION_CONFLICT, "BEFORE_CUTOFF");
                continue;
            }

            if (shortCode == null) {
                invalidFormatCount++;
                addSample(samples, order, DlqReason.INVALID_FORMAT, "INVALID_FORMAT");
                continue;
            }

            if (transactionOptional.isEmpty()) {
                noLocalMatchCount++;
                addSample(samples, order, DlqReason.RECONCILIATION_CONFLICT, "NO_LOCAL_MATCH");
                continue;
            }

            StrategyRunner runnerByShortCode = scopedRunnerByShortCode.get(shortCode);
            if (runnerByShortCode == null || runnerByShortCode.getStatus() == RunnerStatus.ARCHIVED) {
                unknownRunnerCount++;
                addSample(samples, order, DlqReason.UNKNOWN_RUNNER, "RUNNER_NOT_FOUND");
                continue;
            }

            StrategyRunner runner = scopedRunnerById.get(transactionOptional.get().getRunnerId());
            if (runner == null || runner.getStatus() == RunnerStatus.ARCHIVED) {
                unknownRunnerCount++;
                addSample(samples, order, DlqReason.UNKNOWN_RUNNER, "RUNNER_NOT_FOUND");
                continue;
            }

            if (cutoffEnabled && isBeforeCutoff(order.timestamp(), resolveRunnerCutoff(runner))) {
                beforeCutoffCount++;
                addSample(samples, order, DlqReason.RECONCILIATION_CONFLICT, "BEFORE_CUTOFF");
                continue;
            }

            if (!transactionOptional.get().getRunnerId().equals(runnerByShortCode.getId())) {
                unknownRunnerCount++;
                addSample(samples, order, DlqReason.UNKNOWN_RUNNER, "OWNER_MISMATCH");
                continue;
            }

            if (!isOrderSymbolCompatible(order.symbol(), runner.getSymbol())) {
                unknownSymbolCount++;
                addSample(samples, order, DlqReason.UNKNOWN_SYMBOL, "SYMBOL_MISMATCH");
            }
        }

        if (invalidFormatCount == 0
                && unknownRunnerCount == 0
                && noLocalMatchCount == 0
                && beforeCutoffCount == 0
                && unknownSymbolCount == 0) {
            return PortfolioZombieDetectionResult.clean(portfolioId, exchangeId, openOrders.size());
        }

        return PortfolioZombieDetectionResult.detected(
                portfolioId,
                exchangeId,
                openOrders.size(),
                invalidFormatCount,
                unknownRunnerCount,
                noLocalMatchCount,
                beforeCutoffCount,
                unknownSymbolCount,
                samples
        );
    }

    private static void addSample(List<PortfolioZombieCandidate> samples,
                                  OrderDataDto order,
                                  DlqReason reason,
                                  String code) {
        if (samples.size() >= MAX_LOG_SAMPLES) {
            return;
        }
        samples.add(new PortfolioZombieCandidate(
                order.clientOrderId(),
                order.orderId(),
                order.symbol() != null ? order.symbol().toString() : null,
                reason,
                code
        ));
    }

    private static boolean isOrderSymbolCompatible(Symbol orderSymbol, String runnerSymbol) {
        if (runnerSymbol == null || runnerSymbol.isBlank() || orderSymbol == null) {
            return true;
        }
        return runnerSymbol.equalsIgnoreCase(orderSymbol.toString());
    }

    private static Instant resolvePortfolioCutoff(List<StrategyRunner> scopedRunners) {
        return scopedRunners.stream()
                .map(PortfolioZombieDetectionUseCase::resolveRunnerCutoff)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    private static Instant resolveRunnerCutoff(StrategyRunner runner) {
        if (runner.getLastReconciliationAt() != null) {
            return runner.getLastReconciliationAt();
        }
        return runner.getCreatedAt();
    }

    private static boolean isBeforeCutoff(Instant orderTimestamp, Instant cutoff) {
        return cutoff != null && orderTimestamp != null && orderTimestamp.isBefore(cutoff);
    }
}
