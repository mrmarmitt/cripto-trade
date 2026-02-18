package com.marmitt.core.application.usecase.portfolio;

import com.marmitt.core.domain.portfolio.GlobalBalance;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.dto.capital.ExecutionConfirmation;
import com.marmitt.core.dto.events.ExecutionConfirmedEvent;
import com.marmitt.core.ports.inbound.portfolio.HandleExecutionConfirmedPort;
import com.marmitt.core.ports.outbound.repository.GlobalBalanceRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;

/**
 * Implementação de {@link HandleExecutionConfirmedPort} — processa confirmação de execução
 * no Portfolio, convertendo margem de Reserved → Realized no {@code GlobalBalance} (F1-11).
 * <p>
 * Sequência de processamento:
 * <ol>
 *   <li>Idempotência: verifica se {@code matchId} já foi processado via {@code TransactionMatch}</li>
 *   <li>Carrega Runner → obtém {@code portfolioId}</li>
 *   <li>Carrega {@code GlobalBalance}</li>
 *   <li>Aplica {@code confirmExecution(totalCost, ZERO, feeConverted)}</li>
 *   <li>Persiste o {@code GlobalBalance} atualizado</li>
 * </ol>
 * <p>
 * <b>Nota sobre pnlAmount:</b> O PnL realizado por operação é rastreado no {@code TransactionMatch}
 * (Runner). O {@code GlobalBalance.realizedBalance} reflete fluxo de caixa líquido via
 * {@code available += totalCost + pnlAmount}. Em V1, {@code pnlAmount = 0} — o available
 * é restaurado pelo valor total da operação, com o PnL real acompanhado via Runner.
 *
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seções 5.2.2, 5.5.1</a>
 */
@Slf4j
public class HandleExecutionConfirmedUseCase implements HandleExecutionConfirmedPort {

    private final StrategyRunnerRepositoryPort runnerRepository;
    private final GlobalBalanceRepositoryPort globalBalanceRepository;

    public HandleExecutionConfirmedUseCase(
            StrategyRunnerRepositoryPort runnerRepository,
            GlobalBalanceRepositoryPort globalBalanceRepository
    ) {
        this.runnerRepository = runnerRepository;
        this.globalBalanceRepository = globalBalanceRepository;
    }

    @Override
    public void handle(ExecutionConfirmedEvent event) {
        ExecutionConfirmation confirmation = event.confirmation();

        // ── Idempotência: matchId já processado? ─────────────────────────────
        if (runnerRepository.existsTransactionMatchById(confirmation.matchId())) {
            log.info("handleExecutionConfirmed: duplicate matchId={} — discarding silently",
                    confirmation.matchId());
            return;
        }

        // ── Carregar Runner → portfolioId ────────────────────────────────────
        StrategyRunner runner = runnerRepository.findById(confirmation.runnerId())
                .orElseThrow(() -> new IllegalStateException(
                        "Runner not found: " + confirmation.runnerId()));

        // ── Carregar GlobalBalance ────────────────────────────────────────────
        GlobalBalance balance = globalBalanceRepository
                .findByPortfolioId(runner.getPortfolioId())
                .orElseThrow(() -> new IllegalStateException(
                        "GlobalBalance not found for portfolio: " + runner.getPortfolioId()));

        // ── Aplicar conversão Reserved → Realized ────────────────────────────
        BigDecimal feeConverted = confirmation.fee().getConvertedAmountOrZero();
        balance.confirmExecution(confirmation.totalCost(), BigDecimal.ZERO, feeConverted);

        globalBalanceRepository.save(balance);

        log.info("handleExecutionConfirmed: matchId={} transactionId={} totalCost={} fee={} isFinal={}",
                confirmation.matchId(), confirmation.transactionId(),
                confirmation.totalCost(), feeConverted, confirmation.isFinal());
    }
}
