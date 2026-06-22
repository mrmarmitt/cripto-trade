package com.marmitt.core.application.usecase.runner;

import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.runner.request.RecoverStaleTransactionsRequest;
import com.marmitt.core.dto.runner.request.RecoverTransactionStatusRequest;
import com.marmitt.core.dto.runner.response.RecoverStaleTransactionsResponse;
import com.marmitt.core.dto.runner.response.RecoverTransactionStatusResponse;
import com.marmitt.core.enums.TransactionStatus;
import com.marmitt.core.ports.inbound.runner.RecoverStaleTransactionsPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class RecoverStaleTransactionsUseCase implements RecoverStaleTransactionsPort {

    // Confirmados (ja tem exchangeOrderId) usam o stale-threshold normal.
    private static final List<TransactionStatus> CONFIRMED_STATUSES = List.of(
            TransactionStatus.SUBMITTED,
            TransactionStatus.PARTIAL
    );
    // PENDING (reserva orfa) usa uma carencia maior (pendingUpdatedBefore): nao pode ser
    // selecionado enquanto o dispatch+ACK ainda pode estar em voo (persist-first).
    private static final List<TransactionStatus> PENDING_STATUSES = List.of(
            TransactionStatus.PENDING
    );

    private final StrategyRunnerRepositoryPort strategyRunnerRepository;
    private final RecoverTransactionStatusUseCase recoverTransactionStatusUseCase;

    public RecoverStaleTransactionsUseCase(StrategyRunnerRepositoryPort strategyRunnerRepository,
                                           RecoverTransactionStatusUseCase recoverTransactionStatusUseCase) {
        this.strategyRunnerRepository = strategyRunnerRepository;
        this.recoverTransactionStatusUseCase = recoverTransactionStatusUseCase;
    }

    @Override
    public RecoverStaleTransactionsResponse execute(RecoverStaleTransactionsRequest request) {
        if (request.maxPerRun() == 0) {
            return new RecoverStaleTransactionsResponse(0, 0, 0, 0, 0);
        }

        // Dois cutoffs com budgets INDEPENDENTES (cada um ate maxPerRun): confirmados pelo
        // stale-threshold; PENDING por uma carencia maior. Budget separado e proposital — um
        // backlog de confirmados (que permanecem no mesmo status apos DLQ e sao re-selecionados
        // a cada ciclo, ordenados por updated_at) nao pode starvar a limpeza de reserva orfa
        // (PENDING), senao o capital ficaria preso indefinidamente.
        List<Transaction> confirmed = strategyRunnerRepository.findByStatusesUpdatedBefore(
                CONFIRMED_STATUSES,
                request.updatedBefore(),
                request.maxPerRun()
        );
        List<Transaction> pending = strategyRunnerRepository.findByStatusesUpdatedBefore(
                PENDING_STATUSES,
                request.pendingUpdatedBefore(),
                request.maxPerRun()
        );

        List<Transaction> candidates = new ArrayList<>(confirmed.size() + pending.size());
        candidates.addAll(confirmed);
        candidates.addAll(pending);

        int recovered = 0;
        int routedToDlq = 0;
        int skipped = 0;
        int failed = 0;

        for (Transaction candidate : candidates) {
            try {
                // A politica de not-found e resolvida dentro do engine pelo status RECARREGADO
                // (forRuntimeWatchdog -> DERIVE_FROM_STATUS), nunca pelo status do snapshot do lote:
                // se a linha virou SUBMITTED/PARTIAL entre a selecao e o execute, o not-found vira
                // DLQ (e nao expiracao indevida de ordem ja confirmada).
                RecoverTransactionStatusResponse response = recoverTransactionStatusUseCase.execute(
                        RecoverTransactionStatusRequest.forRuntimeWatchdog(candidate.getId()));

                if (response.outcome() == RecoverTransactionStatusResponse.RecoveryOutcome.RECOVERED) {
                    if (response.action() == RecoverTransactionStatusResponse.RecoveryAction.ROUTED_TO_DLQ) {
                        routedToDlq++;
                    } else {
                        recovered++;
                    }
                    continue;
                }

                if (response.outcome() == RecoverTransactionStatusResponse.RecoveryOutcome.SKIPPED) {
                    skipped++;
                    continue;
                }

                failed++;
                log.warn("runtimeRecoveryBatch: failed transactionId={} reason={} failureReason={}",
                        candidate.getId(), response.message(), response.failureReason());
            } catch (RuntimeException e) {
                failed++;
                log.error("runtimeRecoveryBatch: unexpected failure transactionId={}", candidate.getId(), e);
            }
        }

        return new RecoverStaleTransactionsResponse(
                candidates.size(),
                recovered,
                routedToDlq,
                skipped,
                failed
        );
    }
}
