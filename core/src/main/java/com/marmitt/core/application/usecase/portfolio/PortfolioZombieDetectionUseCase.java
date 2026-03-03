package com.marmitt.core.application.usecase.portfolio;

import com.marmitt.core.domain.Symbol;
import com.marmitt.core.domain.runner.ClientOrderId;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.portfolio.PortfolioZombieCandidate;
import com.marmitt.core.dto.portfolio.PortfolioZombieDetectionResult;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.enums.DlqReason;
import com.marmitt.core.enums.RunnerStatus;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeOrderQueryPort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Phase 2 (Portfolio): identifica ordens abertas na exchange sem correspondencia local valida.
 */
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
        Optional<ExchangeOrderQueryPort> orderQueryOptional =
                exchangeAdapterRepository.findOrderQueryByName(exchangeId);
        if (orderQueryOptional.isEmpty()) {
            return PortfolioZombieDetectionResult.skipped(
                    portfolioId, exchangeId, "ORDER_QUERY_NOT_AVAILABLE",
                    "Exchange order query capability is not available.");
        }

        try {
            List<OrderDataDto> openOrders = orderQueryOptional.get().listAllOpenOrders();
            return classifyOpenOrders(portfolioId, exchangeId, openOrders != null ? openOrders : List.of());
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
                                                              List<OrderDataDto> openOrders) {
        int invalidFormatCount = 0;
        int unknownRunnerCount = 0;
        int noLocalMatchCount = 0;
        int unknownSymbolCount = 0;
        List<PortfolioZombieCandidate> samples = new ArrayList<>();

        for (OrderDataDto order : openOrders) {
            String clientOrderId = order.clientOrderId();
            String shortCode = ClientOrderId.getRunnerShortCode(clientOrderId);
            if (shortCode != null) {
                shortCode = shortCode.toLowerCase(Locale.ROOT);
            }

            if (shortCode == null) {
                invalidFormatCount++;
                addSample(samples, order, DlqReason.INVALID_FORMAT, "INVALID_FORMAT");
                continue;
            }

            Optional<Transaction> transactionOptional =
                    strategyRunnerRepository.findTransactionByClientOrderId(clientOrderId);
            if (transactionOptional.isEmpty()) {
                noLocalMatchCount++;
                addSample(samples, order, DlqReason.RECONCILIATION_CONFLICT, "NO_LOCAL_MATCH");
                continue;
            }

            Optional<StrategyRunner> runnerOptional =
                    strategyRunnerRepository.findByShortCodeAndPortfolioId(shortCode, portfolioId);
            if (runnerOptional.isEmpty() || runnerOptional.get().getStatus() == RunnerStatus.ARCHIVED) {
                unknownRunnerCount++;
                addSample(samples, order, DlqReason.UNKNOWN_RUNNER, "RUNNER_NOT_FOUND");
                continue;
            }

            StrategyRunner runner = runnerOptional.get();
            if (!transactionOptional.get().getRunnerId().equals(runner.getId())) {
                unknownRunnerCount++;
                addSample(samples, order, DlqReason.UNKNOWN_RUNNER, "OWNER_MISMATCH");
                continue;
            }

            if (!isOrderSymbolCompatible(order.symbol(), runner.getSymbol())) {
                unknownSymbolCount++;
                addSample(samples, order, DlqReason.UNKNOWN_SYMBOL, "SYMBOL_MISMATCH");
            }
        }

        if (invalidFormatCount == 0 && unknownRunnerCount == 0 && noLocalMatchCount == 0 && unknownSymbolCount == 0) {
            return PortfolioZombieDetectionResult.clean(portfolioId, exchangeId, openOrders.size());
        }

        return PortfolioZombieDetectionResult.detected(
                portfolioId,
                exchangeId,
                openOrders.size(),
                invalidFormatCount,
                unknownRunnerCount,
                noLocalMatchCount,
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
}
