package com.marmitt.core.application.reaction;

import com.marmitt.core.domain.portfolio.GlobalBalance;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.dto.capital.MarginRelease;
import com.marmitt.core.dto.events.MarginReleaseEvent;
import com.marmitt.core.ports.outbound.repository.CapitalEventIdempotencyPort;
import com.marmitt.core.ports.outbound.repository.GlobalBalanceRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import lombok.extern.slf4j.Slf4j;

/**
 * Reaction to {@link MarginReleaseEvent}.
 *
 * <p>Releases reserved capital back to available balance.
 */
@Slf4j
public class MarginReleasedReaction {

    private final StrategyRunnerRepositoryPort runnerRepository;
    private final GlobalBalanceRepositoryPort globalBalanceRepository;
    private final CapitalEventIdempotencyPort idempotencyPort;

    public MarginReleasedReaction(
            StrategyRunnerRepositoryPort runnerRepository,
            GlobalBalanceRepositoryPort globalBalanceRepository,
            CapitalEventIdempotencyPort idempotencyPort
    ) {
        this.runnerRepository = runnerRepository;
        this.globalBalanceRepository = globalBalanceRepository;
        this.idempotencyPort = idempotencyPort;
    }

    public void handle(MarginReleaseEvent event) {
        MarginRelease release = event.release();
        if (!idempotencyPort.tryRegisterMarginRelease(release.transactionId())) {
            log.debug("marginReleasedReaction: duplicate event ignored transactionId={}", release.transactionId());
            return;
        }

        StrategyRunner runner = runnerRepository.findById(release.runnerId())
                .orElseThrow(() -> new IllegalStateException("Runner not found: " + release.runnerId()));

        GlobalBalance balance = globalBalanceRepository
                .findByPortfolioId(runner.getPortfolioId())
                .orElseThrow(() -> new IllegalStateException(
                        "GlobalBalance not found for portfolio: " + runner.getPortfolioId()));

        // Defensive guard against invalid releases.
        if (balance.getReservedBalance().compareTo(release.releaseAmount()) < 0) {
            log.warn("marginReleasedReaction: reservedBalance={} < releaseAmount={} transactionId={} - skipping",
                    balance.getReservedBalance(), release.releaseAmount(), release.transactionId());
            return;
        }

        balance.release(release.releaseAmount());
        globalBalanceRepository.save(balance);

        log.info("marginReleasedReaction: transactionId={} releaseAmount={} reason={} portfolioId={}",
                release.transactionId(), release.releaseAmount(), release.reason(), runner.getPortfolioId());
    }
}
