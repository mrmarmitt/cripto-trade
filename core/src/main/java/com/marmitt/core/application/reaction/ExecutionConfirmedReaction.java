package com.marmitt.core.application.reaction;

import com.marmitt.core.domain.portfolio.GlobalBalance;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.dto.capital.ExecutionConfirmation;
import com.marmitt.core.dto.events.ExecutionConfirmedEvent;
import com.marmitt.core.ports.outbound.repository.GlobalBalanceRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;

/**
 * Reacao ao evento {@link ExecutionConfirmedEvent} publicado pelo Runner apos cada TransactionMatch.
 *
 * <p>Converte margem de Reserved -> Realized no GlobalBalance do Portfolio.
 * Nao representa uma intencao de negocio iniciada por ator externo - e uma consequencia
 * direta do fluxo de conciliacao de ordens (OrderConciliationUseCase).
 *
 * <p>Sequencia de processamento:
 * <ol>
 *   <li>Carrega Runner -> obtem portfolioId</li>
 *   <li>Carrega GlobalBalance</li>
 *   <li>Aplica confirmExecution(totalCost, ZERO, feeConverted)</li>
 *   <li>Persiste o GlobalBalance atualizado</li>
 * </ol>
 *
 * <p><b>Nota sobre pnlAmount:</b> O PnL realizado por operacao e rastreado no TransactionMatch
 * (Runner). O GlobalBalance.realizedBalance reflete fluxo de caixa liquido via
 * available += totalCost + pnlAmount. Em V1, pnlAmount = 0 - o available
 * e restaurado pelo valor total da operacao, com o PnL real acompanhado via Runner.
 */
@Slf4j
public class ExecutionConfirmedReaction {

    private final StrategyRunnerRepositoryPort runnerRepository;
    private final GlobalBalanceRepositoryPort globalBalanceRepository;

    public ExecutionConfirmedReaction(
            StrategyRunnerRepositoryPort runnerRepository,
            GlobalBalanceRepositoryPort globalBalanceRepository
    ) {
        this.runnerRepository = runnerRepository;
        this.globalBalanceRepository = globalBalanceRepository;
    }

    public void handle(ExecutionConfirmedEvent event) {
        ExecutionConfirmation confirmation = event.confirmation();

        // Idempotency: do not check TransactionMatch existence here.
        // The match is persisted before the event is published.

        StrategyRunner runner = runnerRepository.findById(confirmation.runnerId())
                .orElseThrow(() -> new IllegalStateException(
                        "Runner not found: " + confirmation.runnerId()));

        GlobalBalance balance = globalBalanceRepository
                .findByPortfolioId(runner.getPortfolioId())
                .orElseThrow(() -> new IllegalStateException(
                        "GlobalBalance not found for portfolio: " + runner.getPortfolioId()));

        BigDecimal feeConverted = confirmation.fee().getConvertedAmountOrZero();
        balance.confirmExecution(confirmation.totalCost(), BigDecimal.ZERO, feeConverted);

        globalBalanceRepository.save(balance);

        log.info("executionConfirmedReaction: matchId={} transactionId={} totalCost={} fee={} isFinal={}",
                confirmation.matchId(), confirmation.transactionId(),
                confirmation.totalCost(), feeConverted, confirmation.isFinal());
    }
}
