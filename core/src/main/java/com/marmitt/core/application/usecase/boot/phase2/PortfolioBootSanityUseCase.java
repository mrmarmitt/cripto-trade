package com.marmitt.core.application.usecase.boot.phase2;

import com.marmitt.core.domain.portfolio.GlobalBalance;
import com.marmitt.core.dto.portfolio.PortfolioBootSanityResult;
import com.marmitt.core.dto.websocket.data.AccountDataDto;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeAccountQueryPort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import com.marmitt.core.ports.outbound.repository.GlobalBalanceRepositoryPort;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
public class PortfolioBootSanityUseCase {

    private static final BigDecimal DEFAULT_THRESHOLD = new BigDecimal("0.00000001");

    private final GlobalBalanceRepositoryPort globalBalanceRepository;
    private final ExchangeAdapterRepositoryPort exchangeAdapterRepository;

    public PortfolioBootSanityUseCase(GlobalBalanceRepositoryPort globalBalanceRepository,
                                      ExchangeAdapterRepositoryPort exchangeAdapterRepository) {
        this.globalBalanceRepository = globalBalanceRepository;
        this.exchangeAdapterRepository = exchangeAdapterRepository;
    }

    public PortfolioBootSanityResult execute(UUID portfolioId, String exchangeId) {
        return execute(portfolioId, exchangeId, DEFAULT_THRESHOLD);
    }

    public PortfolioBootSanityResult execute(UUID portfolioId,
                                             String exchangeId,
                                             BigDecimal threshold) {
        Optional<GlobalBalance> balanceOptional = globalBalanceRepository.findByPortfolioId(portfolioId);
        if (balanceOptional.isEmpty()) {
            return PortfolioBootSanityResult.failed(
                    portfolioId, exchangeId, "GLOBAL_BALANCE_NOT_FOUND",
                    "GlobalBalance not found for portfolio.");
        }

        Optional<ExchangeAccountQueryPort> accountQuery =
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
            BigDecimal signedDelta = exchangeTotal.subtract(localTotal);
            BigDecimal absoluteDeviation = signedDelta.abs();
            BigDecimal effectiveThreshold = threshold != null ? threshold : DEFAULT_THRESHOLD;

            if (absoluteDeviation.compareTo(effectiveThreshold) <= 0) {
                return PortfolioBootSanityResult.pass(
                        portfolioId, exchangeId, baseCurrency, localTotal, exchangeTotal, signedDelta, absoluteDeviation);
            }

            if (signedDelta.compareTo(BigDecimal.ZERO) > 0) {
                return PortfolioBootSanityResult.warnSurplus(
                        portfolioId, exchangeId, baseCurrency, localTotal, exchangeTotal, signedDelta, absoluteDeviation);
            }

            return PortfolioBootSanityResult.failDeficit(
                    portfolioId, exchangeId, baseCurrency, localTotal, exchangeTotal, signedDelta, absoluteDeviation);
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
}
