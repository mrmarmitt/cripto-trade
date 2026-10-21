package com.marmitt.core.application.usecase.portfolio;

import com.marmitt.core.domain.portfolio.GlobalBalance;
import com.marmitt.core.domain.portfolio.Portfolio;
import com.marmitt.core.domain.runner.Position;
import com.marmitt.core.enums.SafeModeStatus;
import com.marmitt.core.enums.SafeModeTrigger;
import com.marmitt.core.ports.inbound.portfolio.EvaluateDrawdownPort;
import com.marmitt.core.ports.outbound.marketdata.LatestPricePort;
import com.marmitt.core.ports.outbound.metrics.CircuitBreakerMetricsPort;
import com.marmitt.core.ports.outbound.repository.EquitySnapshotRepositoryPort;
import com.marmitt.core.ports.outbound.repository.GlobalBalanceRepositoryPort;
import com.marmitt.core.ports.outbound.repository.PortfolioRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Circuit breaker de capital por drawdown (T43, Blueprint 11.1.A gatilho 1, IG 11.1.2).
 *
 * <p>A cada avaliacao, por portfolio ativo:
 * <ol>
 *   <li><b>Equity</b> = {@code available + reserved + PnL flutuante} das posicoes ativas, marcadas ao
 *       ultimo preco ({@link LatestPricePort}). O realizado ja esta dentro de {@code available}
 *       ({@code confirmExecution}); o custo dos lotes abertos e das ordens em voo esta em
 *       {@code reserved} (T36). Sem preco para alguma posicao a avaliacao do portfolio e pulada:
 *       um equity incompleto gravaria um pico ou um vale falso.</li>
 *   <li>Grava a amostra e calcula o pico desde o inicio da janela: o maior entre
 *       {@code agora - 24h} e o ultimo reset manual (descida para NORMAL). Sem isso, logo apos o reset
 *       o pico antigo reacionaria o HALT.</li>
 *   <li>Se o portfolio esta em NORMAL e {@code (pico - equity) / pico > maxDrawdownPercent}, aciona
 *       HALT automatico pelo {@link AutomaticSafeModeTrigger} (auditoria, metrica e alerta). Nao ha retomada
 *       automatica (Blueprint 11.1.C).</li>
 * </ol>
 */
@Slf4j
public class EvaluateDrawdownUseCase implements EvaluateDrawdownPort {

    static final Duration WINDOW = Duration.ofHours(24);
    private static final BigDecimal WARNING_FRACTION = new BigDecimal("0.5");

    private final PortfolioRepositoryPort portfolioRepository;
    private final GlobalBalanceRepositoryPort globalBalanceRepository;
    private final PortfolioMarkToMarket markToMarket;
    private final EquitySnapshotRepositoryPort equitySnapshots;
    private final AutomaticSafeModeTrigger safeModeTrigger;
    private final CircuitBreakerMetricsPort metrics;
    private final BigDecimal maxDrawdownPercent;
    private final Clock clock;
    /** Lote sem preco ja avisado por portfolio: o aviso sai uma vez, nao a cada ciclo (T58). */
    private final Map<UUID, UUID> unpricedWarned = new ConcurrentHashMap<>();

    public EvaluateDrawdownUseCase(PortfolioRepositoryPort portfolioRepository,
                                   GlobalBalanceRepositoryPort globalBalanceRepository,
                                   StrategyRunnerRepositoryPort strategyRunnerRepository,
                                   LatestPricePort latestPrices,
                                   EquitySnapshotRepositoryPort equitySnapshots,
                                   AutomaticSafeModeTrigger safeModeTrigger,
                                   CircuitBreakerMetricsPort metrics,
                                   BigDecimal maxDrawdownPercent,
                                   Clock clock) {
        if (maxDrawdownPercent == null || maxDrawdownPercent.signum() <= 0 || maxDrawdownPercent.compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalArgumentException("maxDrawdownPercent must be in (0, 1): " + maxDrawdownPercent);
        }
        this.portfolioRepository = portfolioRepository;
        this.globalBalanceRepository = globalBalanceRepository;
        this.markToMarket = new PortfolioMarkToMarket(strategyRunnerRepository, latestPrices);
        this.equitySnapshots = equitySnapshots;
        this.safeModeTrigger = safeModeTrigger;
        this.metrics = metrics;
        this.maxDrawdownPercent = maxDrawdownPercent;
        this.clock = clock;
    }

    @Override
    public void evaluate() {
        Instant now = clock.instant();
        for (Portfolio portfolio : portfolioRepository.findAll()) {
            if (!portfolio.isActive()) {
                continue;
            }
            try {
                evaluate(portfolio, now);
            } catch (RuntimeException e) {
                log.error("drawdown: evaluation failed portfolioId={} - {}", portfolio.getId(), e.getMessage(), e);
            }
        }
        equitySnapshots.deleteBefore(now.minus(WINDOW));
    }

    private void evaluate(Portfolio portfolio, Instant now) {
        Optional<BigDecimal> equity = equityOf(portfolio);
        if (equity.isEmpty()) {
            return;
        }
        equitySnapshots.record(portfolio.getId(), equity.get(), now);

        BigDecimal peak = equitySnapshots.peakSince(portfolio.getId(), windowStart(portfolio, now))
                .map(stored -> stored.max(equity.get()))
                .orElse(equity.get());
        BigDecimal drawdown = peak.signum() > 0
                ? peak.subtract(equity.get()).divide(peak, 8, RoundingMode.HALF_UP).max(BigDecimal.ZERO)
                : BigDecimal.ZERO;
        metrics.recordDrawdown(portfolio.getId(), equity.get(), peak, drawdown);

        if (portfolio.getSafeModeStatus() != SafeModeStatus.NORMAL) {
            return;
        }
        if (drawdown.compareTo(maxDrawdownPercent) > 0) {
            trip(portfolio, equity.get(), peak, drawdown);
        } else if (drawdown.compareTo(maxDrawdownPercent.multiply(WARNING_FRACTION)) > 0) {
            log.warn("drawdown: approaching limit portfolioId={} drawdown={} limit={} peak={} equity={}",
                    portfolio.getId(), drawdown, maxDrawdownPercent, peak, equity.get());
        }
    }

    /**
     * Equity marcado a mercado, ou vazio se alguma posicao ativa ainda nao tem preco conhecido.
     */
    private Optional<BigDecimal> equityOf(Portfolio portfolio) {
        GlobalBalance balance = globalBalanceRepository.findByPortfolioId(portfolio.getId()).orElse(null);
        if (balance == null) {
            return Optional.empty();
        }
        PortfolioMarkToMarket.Valuation valuation = markToMarket.value(portfolio.getId());
        if (!valuation.complete()) {
            Position first = valuation.unpriced().getFirst();
            if (first.getId().equals(unpricedWarned.put(portfolio.getId(), first.getId()))) {
                log.debug("drawdown: still no market price for symbol={} positionId={} - portfolioId={} skipped",
                        first.getSymbol(), first.getId(), portfolio.getId());
            } else {
                log.warn("drawdown: no market price for symbol={} positionId={} - portfolioId={} skipped",
                        first.getSymbol(), first.getId(), portfolio.getId());
            }
            return Optional.empty();
        }
        unpricedWarned.remove(portfolio.getId());
        return Optional.of(balance.getAvailableBalance().add(balance.getReservedBalance())
                .add(valuation.unrealizedPnl()));
    }

    /**
     * Inicio da janela do pico: 24h atras ou o ultimo reset manual para NORMAL, o que for mais recente.
     */
    private static Instant windowStart(Portfolio portfolio, Instant now) {
        Instant start = now.minus(WINDOW);
        Instant resetAt = portfolio.getSafeModeChangedAt();
        if (portfolio.getSafeModeStatus() == SafeModeStatus.NORMAL && resetAt != null && resetAt.isAfter(start)) {
            return resetAt;
        }
        return start;
    }

    private void trip(Portfolio portfolio, BigDecimal equity, BigDecimal peak, BigDecimal drawdown) {
        safeModeTrigger.activate(portfolio.getId(), SafeModeTrigger.MAX_DRAWDOWN_EXCEEDED,
                "drawdown=" + drawdown + " limit=" + maxDrawdownPercent + " peak=" + peak + " equity=" + equity);
    }

}
