package com.marmitt.core.ports.inbound.portfolio;

import com.marmitt.core.dto.capital.CapitalRequest;
import com.marmitt.core.dto.capital.ReservationResult;

/**
 * Port de entrada para reserva de capital (Capital Request).
 * <p>
 * <b>Natureza:</b> Síncrono e bloqueante — strong consistency.
 * Se retornar APPROVED, a margem está garantida no {@code GlobalBalance}.
 * <p>
 * O Runner invoca este port após persistir a Transaction (PENDING) e antes
 * do Order Dispatch. Se retornar REJECTED, a Transaction deve ser marcada
 * como REJECTED e o sinal descartado.
 * <p>
 * Sequência de validações (IG Seção 5.2.1):
 * <ol>
 *   <li>Runner existe e está operacional → {@code UNKNOWN_RUNNER}</li>
 *   <li>Portfolio existe e SafeMode está NORMAL → {@code RISK_VIOLATION}</li>
 *   <li>Limite de alocação do Runner não excedido → {@code RUNNER_LIMIT_EXCEEDED}</li>
 *   <li>Reserva atômica com lock pessimista → {@code INSUFFICIENT_FUNDS}</li>
 * </ol>
 *
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 5.2.1</a>
 */
public interface ReserveCapitalPort {

    /**
     * Solicita reserva de capital para uma nova ordem.
     *
     * @param request payload com transactionId, runnerId, symbol, amount e tipo da operação
     * @return {@link ReservationResult} APPROVED (com reservationId) ou REJECTED (com motivo)
     */
    ReservationResult reserve(CapitalRequest request);
}
