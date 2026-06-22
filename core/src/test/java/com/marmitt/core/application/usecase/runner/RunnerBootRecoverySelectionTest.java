package com.marmitt.core.application.usecase.runner;

import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.enums.TransactionStatus;
import com.marmitt.core.enums.TransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunnerBootRecoverySelectionTest {

    private static final long GRACE_MS = 600_000L; // 10 min
    private final Instant now = Instant.parse("2026-06-22T12:00:00Z");

    @Test
    void youngPendingIsDeferredWhenGracePositive() {
        Transaction young = pending(now.minusSeconds(60)); // 1 min < carencia
        assertTrue(RunnerBootRecoveryUseCase.selectForReconciliation(List.of(young), GRACE_MS, now).isEmpty());
    }

    @Test
    void oldPendingIsReconciledWhenGracePositive() {
        Transaction old = pending(now.minus(2, ChronoUnit.HOURS));
        assertEquals(1, RunnerBootRecoveryUseCase.selectForReconciliation(List.of(old), GRACE_MS, now).size());
    }

    @Test
    void zeroGraceReconcilesAllPendingIncludingYoung() {
        Transaction young = pending(now.minusSeconds(1));
        assertEquals(1, RunnerBootRecoveryUseCase.selectForReconciliation(List.of(young), 0L, now).size(),
                "grace=0 (sem carencia) deve reconciliar TODOS os PENDING, nao deferir");
    }

    @Test
    void submittedIsAlwaysReconciledRegardlessOfAge() {
        Transaction submittedYoung = reconstituted(TransactionStatus.SUBMITTED, now.minusSeconds(1));
        assertEquals(1, RunnerBootRecoveryUseCase.selectForReconciliation(List.of(submittedYoung), GRACE_MS, now).size());
    }

    private Transaction pending(Instant requestedAt) {
        return reconstituted(TransactionStatus.PENDING, requestedAt);
    }

    private Transaction reconstituted(TransactionStatus status, Instant requestedAt) {
        return Transaction.reconstitute()
                .id(UUID.randomUUID())
                .runnerId(UUID.randomUUID())
                .clientOrderId("client-order-id")
                .exchangeOrderId(status == TransactionStatus.PENDING ? null : "EX_ORDER")
                .status(status)
                .type(TransactionType.BUY)
                .symbol("BTCUSDT")
                .quantity(new BigDecimal("0.00150000"))
                .price(new BigDecimal("60000.00000000"))
                .total(new BigDecimal("90.00000000"))
                .requestedAt(requestedAt)
                .updatedAt(requestedAt)
                .build();
    }
}
