package com.marmitt.core.ports.inbound.portfolio;

import com.marmitt.core.dto.capital.CapitalRequest;
import com.marmitt.core.dto.capital.ExecutionConfirmation;
import com.marmitt.core.dto.capital.MarginRelease;
import com.marmitt.core.dto.capital.ReservationResult;

/**
 * Único ponto de acoplamento entre StrategyRunner e Portfolio.
 * O Runner injeta esta interface via Spring DI — nunca referencia Portfolio diretamente.
 * <p>
 * <b>Contrato de comunicação (Seção 5.2):</b>
 * <ul>
 *   <li>{@link #reserve} — síncrono, bloqueante, strong consistency</li>
 *   <li>{@link #confirmExecution} — assíncrono, at-least-once, idempotente por {@code matchId}</li>
 *   <li>{@link #release} — assíncrono, at-least-once sem limite de retry (capital preso é inaceitável)</li>
 * </ul>
 * <p>
 * <b>Implementação V1 (monólito):</b>
 * <ul>
 *   <li>{@code reserve()} → chamada direta de método (síncrono in-process)</li>
 *   <li>{@code confirmExecution()} → {@code ApplicationEventPublisher.publishEvent(ExecutionConfirmedEvent)}</li>
 *   <li>{@code release()} → {@code ApplicationEventPublisher.publishEvent(MarginReleaseEvent)}</li>
 * </ul>
 * Listeners no Portfolio usam {@code @TransactionalEventListener(phase = AFTER_COMMIT)}
 * para garantir que o Runner tenha commitado antes do processamento.
 * <p>
 * Esta interface isola o transporte — migrar para Outbox+Kafka (V2+) exige apenas
 * nova implementação, sem alterar o domínio do Runner.
 *
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seções 5.2, 5.3</a>
 */
public interface CapitalManager {

    /**
     * Solicita reserva de capital para uma nova ordem.
     * <b>Síncrono e bloqueante.</b> Ponto de serialização do GlobalBalance.
     * <p>
     * O Runner deve invocar este método após persistir a Transaction (PENDING)
     * e antes do Order Dispatch. Se retornar REJECTED, a Transaction deve ser
     * marcada como REJECTED e o sinal descartado.
     *
     * @param request payload com transactionId, runnerId, symbol, amount e tipo da operação
     * @return {@link ReservationResult} com status APPROVED (com reservationId) ou REJECTED (com motivo)
     */
    ReservationResult reserve(CapitalRequest request);

    /**
     * Notifica o Portfolio sobre a execução (total ou parcial) de uma ordem.
     * <b>Assíncrono, fire-and-forget com garantia at-least-once.</b>
     * <p>
     * Deve ser invocado após cada {@code TransactionMatch} ser persistido.
     * O Portfolio garante idempotência via {@code matchId} — duplicatas descartadas.
     * Em caso de falha, o evento é reenfileirado com backoff exponencial (max 5 tentativas).
     *
     * @param confirmation payload com matchId, quantidade executada, preço, fee e totalCost
     */
    void confirmExecution(ExecutionConfirmation confirmation);

    /**
     * Solicita devolução de margem reservada quando uma Transaction encerra sem execução total.
     * <b>Assíncrono com retries ilimitados — capital preso (starvation) é inaceitável.</b>
     * <p>
     * Deve ser invocado quando a Transaction atinge REJECTED, CANCELED ou EXPIRED.
     * O Runner persiste o evento localmente para garantir reenvio em caso de crash.
     * O Portfolio garante idempotência via {@code transactionId}.
     *
     * @param release payload com transactionId, releaseAmount, motivo e executedAmount (parcial)
     */
    void release(MarginRelease release);
}
