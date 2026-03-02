package com.marmitt.core.application.usecase.runner.bootrecovery;

import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.enums.TransactionStatus;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Skeleton do Boot Recovery por Runner (Blueprint 6.D / IG 6.6, 9.6, 10.2).
 *
 * <p>Objetivo deste esqueleto:
 * <ul>
 *   <li>Documentar a sequencia de recuperacao sem aplicar side effects ainda.</li>
 *   <li>Servir como base para implementar o fluxo completo em etapas pequenas.</li>
 * </ul>
 *
 * <p>Estado atual: DRY-RUN.
 * Nenhuma transacao, lock ou saldo e alterado por esta classe nesta versao.
 */
@Slf4j
public class RunnerBootRecoveryUseCase {

    private static final List<TransactionStatus> BOOT_RELEVANT_STATUSES = List.of(
            TransactionStatus.PENDING,
            TransactionStatus.SUBMITTED,
            TransactionStatus.PARTIAL
    );

    private final StrategyRunnerRepositoryPort strategyRunnerRepository;

    public RunnerBootRecoveryUseCase(StrategyRunnerRepositoryPort strategyRunnerRepository) {
        this.strategyRunnerRepository = strategyRunnerRepository;
    }

    /**
     * Executa o recovery logico de um runner.
     *
     * <p>Versao atual:
     * <ul>
     *   <li>Carrega transacoes em voo.</li>
     *   <li>Classifica zumbis e limbo.</li>
     *   <li>Registra o plano do que sera feito no fluxo final.</li>
     * </ul>
     */
    public RecoverySummary recoverRunner(StrategyRunner runner) {
        UUID runnerId = runner.getId();
        List<String> notes = new ArrayList<>();

        log.info("bootRecovery: start DRY-RUN runnerId={} status={} reconciling={}",
                runnerId, runner.getStatus(), runner.isReconciling());

        // Step 1 (IG 6.6.2): carregar transacoes relevantes para boot.
        List<Transaction> inFlight = strategyRunnerRepository.findByRunnerIdAndStatuses(
                runnerId, BOOT_RELEVANT_STATUSES);

        // Step 2 (IG 9.6): identificar zumbis = PENDING sem exchangeOrderId.
        List<Transaction> zombies = inFlight.stream()
                .filter(tx -> tx.getStatus() == TransactionStatus.PENDING)
                .filter(tx -> tx.getExchangeOrderId() == null || tx.getExchangeOrderId().isBlank())
                .toList();

        // Step 3 (IG 6.6.2): identificar limbo = PENDING com exchangeOrderId + SUBMITTED + PARTIAL.
        List<Transaction> limbo = inFlight.stream()
                .filter(tx -> !zombies.contains(tx))
                .toList();

        notes.add("TODO[Step 0]: runner.startInitializing() + persistir status INITIALIZING/isReconciling=true.");
        notes.add("TODO[Step 2]: para cada zombie -> EXPIRED + release margem + unlock de lotes.");
        notes.add("TODO[Step 3]: consultar exchange por clientOrderId (FILLED/PARTIAL/CANCELED/NOT_FOUND).");
        notes.add("TODO[Step 4]: aplicar transicoes locais + TransactionMatch + release/confirmExecution.");
        notes.add("TODO[Step 5]: validar integridade final (inflight remanescente, locks ativos, saldo).");
        notes.add("TODO[Step 6]: runner.activate() ou runner.halt() quando houver DLQ/erro irreconciliavel.");

        // Placeholder para futuras fases.
        planZombieResolution(runner, zombies, notes);
        planLimboReconciliation(runner, limbo, notes);

        log.info("bootRecovery: DRY-RUN completed runnerId={} inFlight={} zombies={} limbo={}",
                runnerId, inFlight.size(), zombies.size(), limbo.size());

        return new RecoverySummary(runnerId, inFlight.size(), zombies.size(), limbo.size(), notes);
    }

    private void planZombieResolution(StrategyRunner runner, List<Transaction> zombies, List<String> notes) {
        if (zombies.isEmpty()) {
            notes.add("DRY-RUN: nenhum zombie encontrado para o runner " + runner.getId() + ".");
            return;
        }

        notes.add("DRY-RUN: " + zombies.size()
                + " zombies detectados. Implementacao futura fara rollback atomico por transacao.");
    }

    private void planLimboReconciliation(StrategyRunner runner, List<Transaction> limbo, List<String> notes) {
        if (limbo.isEmpty()) {
            notes.add("DRY-RUN: nenhum limbo encontrado para o runner " + runner.getId() + ".");
            return;
        }

        notes.add("DRY-RUN: " + limbo.size()
                + " transacoes em limbo exigem consulta autoritativa na exchange.");
    }

    public record RecoverySummary(
            UUID runnerId,
            int inFlightCount,
            int zombiesCount,
            int limboCount,
            List<String> notes
    ) {}
}

