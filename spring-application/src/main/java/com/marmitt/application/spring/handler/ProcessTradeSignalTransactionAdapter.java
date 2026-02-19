package com.marmitt.application.spring.handler;

import com.marmitt.core.application.exception.CapitalReservationRejectedException;
import com.marmitt.core.application.usecase.runner.ProcessTradeSignalService;
import com.marmitt.core.domain.runner.Position;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.capital.CapitalRequest;
import com.marmitt.core.ports.inbound.runner.ProcessTradeSignalTransactionPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adapter Spring do fluxo ProcessTradeSignal — gerencia os limites {@code @Transactional}.
 * <p>
 * Responsabilidade única: fornecer as fronteiras transacionais do protocolo Persist-First
 * ao {@link com.marmitt.core.application.usecase.runner.ProcessTradeSignalUseCase}.
 * Toda a lógica de domínio está no UseCase e em
 * {@link com.marmitt.core.application.usecase.runner.ProcessTradeSignalService}.
 *
 * @see ProcessTradeSignalTransactionPort
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 6.2.1</a>
 */
@Component
public class ProcessTradeSignalTransactionAdapter implements ProcessTradeSignalTransactionPort {

    private final StrategyRunnerRepositoryPort runnerRepository;
    private final ProcessTradeSignalService service;

    public ProcessTradeSignalTransactionAdapter(
            StrategyRunnerRepositoryPort runnerRepository,
            ProcessTradeSignalService service
    ) {
        this.runnerRepository = runnerRepository;
        this.service = service;
    }

    /**
     * Tx 1 — persiste Transaction PENDING, aplica lock de Position (SELL) e reserva capital.
     * Rollback automático se {@link CapitalReservationRejectedException} for lançada.
     */
    @Override
    @Transactional(rollbackFor = CapitalReservationRejectedException.class)
    public void persistAndReserve(Transaction transaction, Position targetPosition,
                                  CapitalRequest capitalRequest) {
        if (targetPosition != null) {
            targetPosition.lock(transaction.getId(), transaction.getQuantity());
            targetPosition.startClosing();
            runnerRepository.saveAtomicTransactionAndPositionLock(transaction, targetPosition);
        } else {
            runnerRepository.saveTransaction(transaction);
        }
        service.reserveCapital(capitalRequest);
    }

    /**
     * Tx 2 — persiste Transaction como SUBMITTED após ACK ACCEPTED da exchange.
     */
    @Override
    @Transactional
    public void persistSubmitted(Transaction transaction) {
        runnerRepository.saveTransaction(transaction);
    }
}
