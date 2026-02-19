package com.marmitt.core.ports.inbound.runner;

import com.marmitt.core.application.exception.CapitalReservationRejectedException;
import com.marmitt.core.domain.runner.Position;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.capital.CapitalRequest;

/**
 * Port para as operações transacionais do fluxo ProcessTradeSignal.
 * <p>
 * Permite que {@link com.marmitt.core.application.usecase.runner.ProcessTradeSignalUseCase}
 * (core) invoque operações que requerem {@code @Transactional} sem depender do Spring.
 * <p>
 * Cada método representa um limite de transação distinto do protocolo Persist-First:
 * <ul>
 *   <li>{@link #persistAndReserve}: Tx 1 — persiste PENDING + lock Position + reserva capital.
 *       Rollback automático se {@link CapitalReservationRejectedException} for lançada.</li>
 *   <li>{@link #persistSubmitted}: Tx 2 — persiste Transaction como SUBMITTED (ACK ACCEPTED).</li>
 * </ul>
 *
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 6.2.1</a>
 */
public interface ProcessTradeSignalTransactionPort {

    /**
     * Persiste a Transaction PENDING, aplica lock na Position (SELL) e reserva capital
     * — tudo dentro de uma única transação de banco.
     * <p>
     * Se a reserva de capital for rejeitada, lança {@link CapitalReservationRejectedException}
     * para acionar rollback automático da persistência.
     *
     * @param transaction    transaction com status PENDING
     * @param targetPosition position a bloquear (apenas SELL); null para BUY
     * @param capitalRequest request de reserva de capital
     * @throws CapitalReservationRejectedException se a reserva de capital falhar
     */
    void persistAndReserve(Transaction transaction, Position targetPosition, CapitalRequest capitalRequest);

    /**
     * Persiste a Transaction com status SUBMITTED após ACK ACCEPTED da exchange.
     *
     * @param transaction transaction com status já atualizado para SUBMITTED
     */
    void persistSubmitted(Transaction transaction);
}
