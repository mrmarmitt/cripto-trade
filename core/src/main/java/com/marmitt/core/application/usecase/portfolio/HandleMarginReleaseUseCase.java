package com.marmitt.core.application.usecase.portfolio;

import com.marmitt.core.domain.portfolio.GlobalBalance;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.dto.capital.MarginRelease;
import com.marmitt.core.dto.events.MarginReleaseEvent;
import com.marmitt.core.ports.inbound.portfolio.HandleMarginReleasePort;
import com.marmitt.core.ports.outbound.repository.GlobalBalanceRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementação de {@link HandleMarginReleasePort} — processa estorno de margem
 * no Portfolio, devolvendo capital de Reserved → Available no {@code GlobalBalance} (F1-11).
 * <p>
 * Sequência de processamento:
 * <ol>
 *   <li>Carrega Runner → obtém {@code portfolioId}</li>
 *   <li>Carrega {@code GlobalBalance}</li>
 *   <li>Guard de idempotência: verifica {@code reservedBalance >= releaseAmount}</li>
 *   <li>Aplica {@code release(releaseAmount)}</li>
 *   <li>Persiste o {@code GlobalBalance} atualizado</li>
 * </ol>
 * <p>
 * <b>Idempotência V1:</b> sem tabela de rastreamento de releases individuais,
 * a guarda é feita verificando se {@code reservedBalance >= releaseAmount}.
 * Se insuficiente, o release é descartado com WARN — indica possível duplicata.
 * Rastreamento por {@code transactionId} está previsto para V2+ com Outbox Pattern.
 *
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seções 5.2.3, 5.5.1</a>
 */
@Slf4j
public class HandleMarginReleaseUseCase implements HandleMarginReleasePort {

    private final StrategyRunnerRepositoryPort runnerRepository;
    private final GlobalBalanceRepositoryPort globalBalanceRepository;

    public HandleMarginReleaseUseCase(
            StrategyRunnerRepositoryPort runnerRepository,
            GlobalBalanceRepositoryPort globalBalanceRepository
    ) {
        this.runnerRepository = runnerRepository;
        this.globalBalanceRepository = globalBalanceRepository;
    }

    @Override
    public void handle(MarginReleaseEvent event) {
        MarginRelease release = event.release();

        // ── Carregar Runner → portfolioId ────────────────────────────────────
        StrategyRunner runner = runnerRepository.findById(release.runnerId())
                .orElseThrow(() -> new IllegalStateException(
                        "Runner not found: " + release.runnerId()));

        // ── Carregar GlobalBalance ────────────────────────────────────────────
        GlobalBalance balance = globalBalanceRepository
                .findByPortfolioId(runner.getPortfolioId())
                .orElseThrow(() -> new IllegalStateException(
                        "GlobalBalance not found for portfolio: " + runner.getPortfolioId()));

        // ── Guard de idempotência (V1) ────────────────────────────────────────
        if (balance.getReservedBalance().compareTo(release.releaseAmount()) < 0) {
            log.warn("handleMarginRelease: reservedBalance={} < releaseAmount={} for transactionId={} — " +
                            "possible duplicate release, skipping",
                    balance.getReservedBalance(), release.releaseAmount(), release.transactionId());
            return;
        }

        // ── Aplicar devolução Reserved → Available ────────────────────────────
        balance.release(release.releaseAmount());
        globalBalanceRepository.save(balance);

        log.info("handleMarginRelease: transactionId={} releaseAmount={} reason={} portfolioId={}",
                release.transactionId(), release.releaseAmount(),
                release.reason(), runner.getPortfolioId());
    }
}
