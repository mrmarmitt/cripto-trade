package com.marmitt.core.application.usecase.runner;

import com.marmitt.core.application.usecase.runner.orderconciliation.ConciliationOrderUpdateExecutor;
import com.marmitt.core.dto.runner.request.RecoverTransactionStatusRequest;
import com.marmitt.core.ports.outbound.repository.DeadLetterEntryRepositoryPort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * T26: o recovery por transação ancora o {@code transactionId} no MDC durante todo o
 * {@code execute} — cobrindo runtime watchdog e boot, que passam por este mesmo engine — e
 * limpa-o ao final.
 */
class RecoverTransactionStatusMdcTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void putsTransactionIdInMdcDuringExecuteAndClearsAfter() {
        StrategyRunnerRepositoryPort repository = mock(StrategyRunnerRepositoryPort.class);
        ExchangeAdapterRepositoryPort exchangeAdapterRepository = mock(ExchangeAdapterRepositoryPort.class);
        DeadLetterEntryRepositoryPort deadLetterEntryRepository = mock(DeadLetterEntryRepositoryPort.class);
        ConciliationOrderUpdateExecutor conciliationExecutor = mock(ConciliationOrderUpdateExecutor.class);

        UUID transactionId = UUID.randomUUID();
        AtomicReference<String> mdcDuringExecute = new AtomicReference<>();
        // findTransactionById é a primeira interação do execute: o MDC já deve estar ancorado.
        when(repository.findTransactionById(any())).thenAnswer(invocation -> {
            mdcDuringExecute.set(MDC.get("transactionId"));
            return Optional.empty();
        });

        RecoverTransactionStatusUseCase useCase = new RecoverTransactionStatusUseCase(
                repository, exchangeAdapterRepository, deadLetterEntryRepository, conciliationExecutor);

        useCase.execute(new RecoverTransactionStatusRequest(transactionId));

        assertEquals(transactionId.toString(), mdcDuringExecute.get(),
                "transactionId deve estar no MDC durante o recovery da transação");
        assertNull(MDC.get("transactionId"), "MDC deve ser limpo ao final do execute");
    }
}
