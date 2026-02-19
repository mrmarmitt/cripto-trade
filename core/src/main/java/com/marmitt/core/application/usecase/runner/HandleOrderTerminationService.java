package com.marmitt.core.application.usecase.runner;

import com.marmitt.core.application.usecase.portfolio.ReleaseMarginService;
import com.marmitt.core.domain.runner.Position;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.capital.MarginRelease;
import com.marmitt.core.enums.PositionStatus;
import com.marmitt.core.enums.ReleaseReason;
import com.marmitt.core.enums.TransactionStatus;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Serviço central do fluxo HandleOrderTermination (F2-03).
 * <p>
 * Processa o encerramento de Transactions que atingiram status terminal
 * ({@code REJECTED}, {@code CANCELED}, {@code EXPIRED}), realizando:
 * <ol>
 *   <li>Desbloqueio da Position alvo (apenas para SELL)</li>
 *   <li>Cálculo do montante a ser estornado</li>
 *   <li>Publicação do {@code MarginReleaseEvent} via {@link ReleaseMarginService}</li>
 * </ol>
 * <p>
 * <b>Cálculo do release:</b><br>
 * O montante reservado foi {@code transaction.total × 1.005} (safety buffer aplicado em
 * {@code ProcessTradeSignalService}). Para cada motivo de encerramento:
 * <ul>
 *   <li>{@code REJECTED} / {@code EXPIRED}: estorno total — {@code reserved}</li>
 *   <li>{@code CANCELED}: estorno parcial — {@code reserved − executedValue}.
 *       Se {@code ≤ 0}, o capital já foi integralmente confirmado via
 *       {@code ExecutionConfirmedEvent}; nenhum release é necessário.</li>
 * </ul>
 * <p>
 * <b>Idempotência:</b> garantida pelo {@code HandleMarginReleaseService} (Portfolio side),
 * que verifica {@code reservedBalance >= releaseAmount} antes de aplicar o estorno.
 *
 * @implNote Pertence ao fluxo <b>HandleOrderTermination</b>.
 *           Triggers: dispatch REJECTED (F2-01), exchange callbacks (F2-02),
 *           Watchdog timeout (F2-06/F2-07).
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 6.3, 5.2.3</a>
 */
@Slf4j
public class HandleOrderTerminationService {

    /**
     * Safety buffer aplicado na reserva de capital (deve ser idêntico ao de
     * {@code ProcessTradeSignalService#SAFETY_BUFFER}).
     */
    private static final BigDecimal SAFETY_BUFFER = new BigDecimal("1.005");

    private final StrategyRunnerRepositoryPort runnerRepository;
    private final ReleaseMarginService releaseMarginService;

    public HandleOrderTerminationService(
            StrategyRunnerRepositoryPort runnerRepository,
            ReleaseMarginService releaseMarginService
    ) {
        this.runnerRepository = runnerRepository;
        this.releaseMarginService = releaseMarginService;
    }

    // ============================================================
    // Position unlock
    // ============================================================

    /**
     * Localiza e desbloqueia a Position vinculada à Transaction de SELL.
     * <p>
     * Retorna a Position modificada (pronta para persistência pelo Handler)
     * ou {@code Optional.empty()} se:
     * <ul>
     *   <li>A Transaction é BUY (não há lock de Position)</li>
     *   <li>Nenhuma posição encontrada para o símbolo do Runner</li>
     *   <li>A posição encontrada não está bloqueada por esta Transaction</li>
     * </ul>
     *
     * @param transaction transaction em estado terminal
     * @return Position com lock removido e status reaberto, se aplicável
     */
    public Optional<Position> findAndUnlockPosition(Transaction transaction) {
        if (!transaction.isSell()) {
            return Optional.empty();
        }

        Optional<Position> positionOpt = Optional.empty();

        // Preferência pelo targetLotId (FIFO/LIFO explícito)
        if (transaction.getTargetLotId() != null) {
            positionOpt = runnerRepository.findPositionById(transaction.getTargetLotId());
        }

        // Fallback: posição aberta do símbolo no Runner
        if (positionOpt.isEmpty()) {
            positionOpt = runnerRepository.findOpenPositionByRunnerIdAndSymbol(
                    transaction.getRunnerId(), transaction.getSymbol());
        }

        return positionOpt
                .filter(pos -> transaction.getId().equals(pos.getLockedByTransactionId()))
                .map(pos -> {
                    pos.unlock();
                    if (pos.getStatus() == PositionStatus.CLOSING) {
                        pos.reopen();
                    }
                    log.debug("handleOrderTermination: position unlocked positionId={} transactionId={}",
                            pos.getId(), transaction.getId());
                    return pos;
                });
    }

    // ============================================================
    // Margin release
    // ============================================================

    /**
     * Calcula o montante a estornar e publica o {@code MarginReleaseEvent}.
     * <p>
     * Para {@code CANCELED} com {@code executedValue ≥ reserved}, o estorno é zero
     * (capital já totalmente confirmado pelo HandleExecutionConfirmedService) e
     * nenhum evento é publicado.
     *
     * @param transaction transaction em estado terminal com status já atualizado
     */
    public void releaseMargin(Transaction transaction) {
        TransactionStatus status = transaction.getStatus();
        if (!status.isFinal() || status == TransactionStatus.FILLED) {
            throw new IllegalArgumentException(
                    "releaseMargin requires a failed terminal status, got: " + status);
        }

        BigDecimal reserved = transaction.getTotal()
                .multiply(SAFETY_BUFFER)
                .setScale(8, RoundingMode.HALF_UP);

        MarginRelease release;

        if (status == TransactionStatus.REJECTED || status == TransactionStatus.EXPIRED) {
            release = MarginRelease.fullRelease(
                    transaction.getId(),
                    transaction.getRunnerId(),
                    reserved,
                    mapToReleaseReason(status)
            );

        } else { // CANCELED
            BigDecimal executedValue = transaction.getExecutedValue();
            BigDecimal toRelease = reserved.subtract(executedValue)
                    .setScale(8, RoundingMode.HALF_UP);

            if (toRelease.compareTo(BigDecimal.ZERO) <= 0) {
                log.info("handleOrderTermination: no margin to release for CANCELED " +
                                "transactionId={} (reserved={} executedValue={})",
                        transaction.getId(), reserved, executedValue);
                return;
            }

            release = MarginRelease.partialRelease(
                    transaction.getId(),
                    transaction.getRunnerId(),
                    toRelease,
                    executedValue
            );
        }

        releaseMarginService.release(release);
        log.info("handleOrderTermination: margin release published transactionId={} amount={} reason={}",
                transaction.getId(), release.releaseAmount(), release.reason());
    }

    // ============================================================
    // Private helpers
    // ============================================================

    private ReleaseReason mapToReleaseReason(TransactionStatus status) {
        return switch (status) {
            case REJECTED -> ReleaseReason.REJECTED;
            case EXPIRED  -> ReleaseReason.EXPIRED;
            case CANCELED -> ReleaseReason.CANCELED;
            default -> throw new IllegalArgumentException("Cannot map to ReleaseReason: " + status);
        };
    }
}
