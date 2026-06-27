package com.marmitt.core.application.usecase.runner;

import com.marmitt.core.domain.runner.ClientOrderId;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.runner.request.RecoverStaleTransactionsRequest;
import com.marmitt.core.dto.runner.response.RecoverTransactionStatusResponse;
import com.marmitt.core.enums.TransactionStatus;
import com.marmitt.core.enums.TransactionType;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * T26: o watchdog ancora o {@code transactionId} no MDC enquanto processa cada transacao — cobrindo
 * tanto o recovery/conciliacao chamados abaixo quanto os logs de resumo/falha do proprio loop
 * (runtimeRecoveryBatch: ...), que sao emitidos apos o engine retornar — e limpa ao final.
 */
class RecoverStaleTransactionsMdcTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void putsTransactionIdInMdcDuringEngineCallAndClearsAfter() {
        StrategyRunnerRepositoryPort repository = mock(StrategyRunnerRepositoryPort.class);
        RecoverTransactionStatusUseCase engine = mock(RecoverTransactionStatusUseCase.class);

        Transaction candidate = newConfirmedTransaction();
        when(repository.findByStatusesUpdatedBefore(
                eq(List.of(TransactionStatus.SUBMITTED, TransactionStatus.PARTIAL)), any(), anyInt()))
                .thenReturn(List.of(candidate));
        when(repository.findByStatusesUpdatedBefore(
                eq(List.of(TransactionStatus.PENDING)), any(), anyInt()))
                .thenReturn(List.of());

        AtomicReference<String> mdcDuringEngine = new AtomicReference<>();
        when(engine.execute(any())).thenAnswer(invocation -> {
            mdcDuringEngine.set(MDC.get("transactionId"));
            return RecoverTransactionStatusResponse.skipped(
                    candidate.getId(), UUID.randomUUID(), "BINANCE",
                    TransactionStatus.SUBMITTED, "not eligible");
        });

        RecoverStaleTransactionsUseCase useCase = new RecoverStaleTransactionsUseCase(repository, engine);
        useCase.execute(new RecoverStaleTransactionsRequest(Instant.now(), Instant.now(), 10));

        assertEquals(candidate.getId().toString(), mdcDuringEngine.get(),
                "transactionId deve estar no MDC durante o processamento da transacao");
        assertNull(MDC.get("transactionId"), "MDC deve ser limpo apos o loop");
    }

    private static Transaction newConfirmedTransaction() {
        BigDecimal quantity = new BigDecimal("2.50000000");
        BigDecimal price = new BigDecimal("10.00000000");
        Transaction transaction = new Transaction(
                UUID.randomUUID(),
                ClientOrderId.generate("x1", TransactionType.BUY),
                TransactionType.BUY,
                "BTCUSDT",
                quantity,
                price,
                quantity.multiply(price),
                new BigDecimal("0.8"),
                "test",
                null
        );
        transaction.submit("EX_ORDER_1");
        return transaction;
    }
}
