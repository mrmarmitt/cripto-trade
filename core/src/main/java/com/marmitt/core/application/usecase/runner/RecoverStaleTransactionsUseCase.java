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

import java.util.List;

@Slf4j
public class RecoverStaleTransactionsUseCase implements RecoverStaleTransactionsPort {

    private static final List<TransactionStatus> ELIGIBLE_STATUSES = List.of(
            TransactionStatus.SUBMITTED,
            TransactionStatus.PARTIAL
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

        List<Transaction> candidates = strategyRunnerRepository.findByStatusesUpdatedBefore(
                ELIGIBLE_STATUSES,
                request.updatedBefore(),
                request.maxPerRun()
        );

        int recovered = 0;
        int routedToDlq = 0;
        int skipped = 0;
        int failed = 0;

        for (Transaction candidate : candidates) {
            try {
                RecoverTransactionStatusResponse response = recoverTransactionStatusUseCase.execute(
                        RecoverTransactionStatusRequest.forRuntimeWatchdog(candidate.getId())
                );

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
