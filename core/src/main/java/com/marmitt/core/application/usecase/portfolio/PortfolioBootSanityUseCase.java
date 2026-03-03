package com.marmitt.core.application.usecase.portfolio;

import com.marmitt.core.domain.portfolio.GlobalBalance;
import com.marmitt.core.dto.websocket.data.AccountDataDto;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import com.marmitt.core.ports.outbound.repository.GlobalBalanceRepositoryPort;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Phase 2 (MVP): sanity check de caixa por portfolio/exchange.
 *
 * <p>Compara o total local do Portfolio (available + reserved) com o total
 * reportado pela exchange na moeda base do GlobalBalance.
 */
@Slf4j
public class PortfolioBootSanityUseCase {

    private static final BigDecimal DEFAULT_TOLERANCE = new BigDecimal("0.00010000");
    private static final BigDecimal DEFAULT_PERCENT_TOLERANCE = new BigDecimal("0.10");

    private final GlobalBalanceRepositoryPort globalBalanceRepository;
    private final ExchangeAdapterRepositoryPort exchangeAdapterRepository;

    public PortfolioBootSanityUseCase(GlobalBalanceRepositoryPort globalBalanceRepository,
                                      ExchangeAdapterRepositoryPort exchangeAdapterRepository) {
        this.globalBalanceRepository = globalBalanceRepository;
        this.exchangeAdapterRepository = exchangeAdapterRepository;
    }

    public PortfolioBootSanityResult execute(UUID portfolioId, String exchangeId) {
        return execute(portfolioId, exchangeId, DEFAULT_TOLERANCE, DEFAULT_PERCENT_TOLERANCE);
    }

    public PortfolioBootSanityResult execute(UUID portfolioId,
                                             String exchangeId,
                                             BigDecimal absoluteTolerance,
                                             BigDecimal percentTolerance) {
        Optional<GlobalBalance> balanceOptional = globalBalanceRepository.findByPortfolioId(portfolioId);
        if (balanceOptional.isEmpty()) {
            return PortfolioBootSanityResult.failed(
                    portfolioId, exchangeId, "GLOBAL_BALANCE_NOT_FOUND",
                    "GlobalBalance not found for portfolio.");
        }

        Optional<com.marmitt.core.ports.outbound.exchange.rest.ExchangeAccountQueryPort> accountQuery =
                exchangeAdapterRepository.findAccountQueryByName(exchangeId);
        if (accountQuery.isEmpty()) {
            return PortfolioBootSanityResult.skipped(
                    portfolioId, exchangeId, "ACCOUNT_QUERY_NOT_AVAILABLE",
                    "Exchange account query capability is not available.");
        }

        try {
            GlobalBalance local = balanceOptional.get();
            AccountDataDto account = accountQuery.get().queryAccountSnapshot();

            String baseCurrency = local.getBaseCurrency().toUpperCase();
            BigDecimal localTotal = local.getTotalBalance();
            BigDecimal exchangeTotal = readTotalForCurrency(account, baseCurrency);
            BigDecimal drift = localTotal.subtract(exchangeTotal).abs();
            BigDecimal driftPercent = calculateDriftPercent(drift, exchangeTotal, localTotal);

            boolean withinAbsolute = absoluteTolerance != null && absoluteTolerance.compareTo(BigDecimal.ZERO) >= 0
                    && drift.compareTo(absoluteTolerance) <= 0;
            boolean withinPercent = percentTolerance != null && percentTolerance.compareTo(BigDecimal.ZERO) >= 0
                    && driftPercent.compareTo(percentTolerance) <= 0;

            if (withinAbsolute || withinPercent) {
                return PortfolioBootSanityResult.ok(
                        portfolioId, exchangeId, baseCurrency, localTotal, exchangeTotal, drift, driftPercent);
            }

            return PortfolioBootSanityResult.failed(
                    portfolioId, exchangeId, "BALANCE_DRIFT",
                    "Detected balance drift above tolerance: " + drift.toPlainString(),
                    baseCurrency, localTotal, exchangeTotal, drift, driftPercent
            );
        } catch (UnsupportedOperationException e) {
            return PortfolioBootSanityResult.skipped(
                    portfolioId, exchangeId, "ACCOUNT_QUERY_UNSUPPORTED", e.getMessage());
        } catch (Exception e) {
            log.warn("portfolioBootSanity: failed portfolioId={} exchange={} reason={}",
                    portfolioId, exchangeId, e.getMessage());
            return PortfolioBootSanityResult.failed(
                    portfolioId, exchangeId, "ACCOUNT_QUERY_FAILED", e.getMessage());
        }
    }

    private static BigDecimal readTotalForCurrency(AccountDataDto account, String currency) {
        Map<String, BigDecimal> available = account.balances();
        Map<String, BigDecimal> locked = account.lockedBalances();
        BigDecimal exchangeAvailable = available != null
                ? available.getOrDefault(currency, BigDecimal.ZERO)
                : BigDecimal.ZERO;
        BigDecimal exchangeLocked = locked != null
                ? locked.getOrDefault(currency, BigDecimal.ZERO)
                : BigDecimal.ZERO;
        return exchangeAvailable.add(exchangeLocked);
    }

    private static BigDecimal calculateDriftPercent(BigDecimal drift, BigDecimal exchangeTotal, BigDecimal localTotal) {
        BigDecimal denominator = exchangeTotal;
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            denominator = localTotal;
        }
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return drift
                .multiply(BigDecimal.valueOf(100))
                .divide(denominator, 8, java.math.RoundingMode.HALF_UP);
    }

    public enum SanityStatus {
        OK,
        SKIPPED,
        FAILED
    }

    public record PortfolioBootSanityResult(
            UUID portfolioId,
            String exchangeId,
            SanityStatus status,
            String code,
            String message,
            String baseCurrency,
            BigDecimal localTotal,
            BigDecimal exchangeTotal,
            BigDecimal absoluteDrift,
            BigDecimal driftPercent
    ) {
        static PortfolioBootSanityResult ok(UUID portfolioId,
                                            String exchangeId,
                                            String baseCurrency,
                                            BigDecimal localTotal,
                                            BigDecimal exchangeTotal,
                                            BigDecimal absoluteDrift,
                                            BigDecimal driftPercent) {
            return new PortfolioBootSanityResult(
                    portfolioId,
                    exchangeId,
                    SanityStatus.OK,
                    "OK",
                    "Portfolio sanity check passed.",
                    baseCurrency,
                    localTotal,
                    exchangeTotal,
                    absoluteDrift,
                    driftPercent
            );
        }

        static PortfolioBootSanityResult skipped(UUID portfolioId,
                                                 String exchangeId,
                                                 String code,
                                                 String message) {
            return new PortfolioBootSanityResult(
                    portfolioId,
                    exchangeId,
                    SanityStatus.SKIPPED,
                    code,
                    message,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }

        static PortfolioBootSanityResult failed(UUID portfolioId,
                                                String exchangeId,
                                                String code,
                                                String message) {
            return new PortfolioBootSanityResult(
                    portfolioId,
                    exchangeId,
                    SanityStatus.FAILED,
                    code,
                    message,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }

        static PortfolioBootSanityResult failed(UUID portfolioId,
                                                String exchangeId,
                                                String code,
                                                String message,
                                                String baseCurrency,
                                                BigDecimal localTotal,
                                                BigDecimal exchangeTotal,
                                                BigDecimal absoluteDrift,
                                                BigDecimal driftPercent) {
            return new PortfolioBootSanityResult(
                    portfolioId,
                    exchangeId,
                    SanityStatus.FAILED,
                    code,
                    message,
                    baseCurrency,
                    localTotal,
                    exchangeTotal,
                    absoluteDrift,
                    driftPercent
            );
        }
    }
}
