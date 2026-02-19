package com.marmitt.application.spring.handler;

import com.marmitt.core.application.usecase.portfolio.ReleaseMarginService;
import com.marmitt.core.domain.runner.Position;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.capital.MarginRelease;
import com.marmitt.core.enums.PositionStatus;
import com.marmitt.core.enums.ReleaseReason;
import com.marmitt.core.enums.TransactionStatus;
import com.marmitt.core.ports.inbound.runner.HandleOrderTerminationPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Handler Spring do fluxo HandleOrderTermination (F2-03).
 * <p>
 * Gerencia o limite {@code @Transactional} para o encerramento de Transactions
 * que atingiram status terminal ({@code REJECTED}, {@code CANCELED}, {@code EXPIRED}).
 * <p>
 * <b>Fluxo dentro da transação de banco:</b>
 * <ol>
 *   <li>Desbloquear Position (SELL) — {@code unlock()} + {@code reopen()} + {@code savePosition()}</li>
 *   <li>Persistir Transaction com novo status terminal — {@code saveTransaction()}</li>
 *   <li>Publicar {@code MarginReleaseEvent} — processado pelo Portfolio após o commit
 *       via {@code @TransactionalEventListener(AFTER_COMMIT)}</li>
 * </ol>
 * <p>
 * <b>Reusabilidade:</b> este handler é o ponto de entrada único para todos os triggers
 * de HandleOrderTermination:
 * <ul>
 *   <li>Dispatch REJECTED (F2-01): chamado por {@code ProcessTradeSignalHandler.handleAck()}</li>
 *   <li>Exchange callback — CANCELED/EXPIRED (F2-02): chamado pelo callback handler</li>
 *   <li>Watchdog timeout (F2-06/F2-07): chamado pelo Watchdog</li>
 * </ul>
 * <p>
 * <b>Cálculo do release:</b><br>
 * O montante reservado foi {@code transaction.total × 1.005} (safety buffer aplicado em
 * {@code ProcessTradeSignalService}). Para cada motivo de encerramento:
 * <ul>
 *   <li>{@code REJECTED} / {@code EXPIRED}: estorno total — {@code reserved}</li>
 *   <li>{@code CANCELED}: estorno parcial — {@code reserved − executedValue}.
 *       Se {@code ≤ 0}, o capital já foi integralmente confirmado; nenhum release é necessário.</li>
 * </ul>
 *
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 6.3, 5.2.3</a>
 */
@Component
@Slf4j
public class HandleOrderTerminationHandler implements HandleOrderTerminationPort {

    /**
     * Safety buffer aplicado na reserva de capital (deve ser idêntico ao de
     * {@code ProcessTradeSignalService#SAFETY_BUFFER}).
     */
    private static final BigDecimal SAFETY_BUFFER = new BigDecimal("1.005");

    private final StrategyRunnerRepositoryPort runnerRepository;
    private final ReleaseMarginService releaseMarginService;

    public HandleOrderTerminationHandler(
            StrategyRunnerRepositoryPort runnerRepository,
            ReleaseMarginService releaseMarginService
    ) {
        this.runnerRepository = runnerRepository;
        this.releaseMarginService = releaseMarginService;
    }

    /**
     * Processa o encerramento de uma Transaction em status terminal.
     * <p>
     * <b>Pré-condição:</b> {@code transaction.getStatus()} já deve estar atualizado
     * para o status terminal ({@code reject()}, {@code cancel()} ou {@code expire()}
     * chamados antes deste handler).
     *
     * @param transaction transaction com status terminal já aplicado
     */
    @Transactional
    public void handle(Transaction transaction) {
        log.debug("handleOrderTermination: processing transactionId={} status={}",
                transaction.getId(), transaction.getStatus());

        // Passo 1: desbloquear Position (SELL only)
        Optional<Position> unlockedPosition = findAndUnlockPosition(transaction);
        unlockedPosition.ifPresent(runnerRepository::savePosition);

        // Passo 2: persistir Transaction com status terminal
        runnerRepository.saveTransaction(transaction);

        // Passo 3: publicar MarginReleaseEvent (processado AFTER_COMMIT pelo Portfolio)
        releaseMargin(transaction);
    }

    // ============================================================
    // Position unlock
    // ============================================================

    private Optional<Position> findAndUnlockPosition(Transaction transaction) {
        if (!transaction.isSell()) {
            return Optional.empty();
        }

        Optional<Position> positionOpt = Optional.empty();

        if (transaction.getTargetLotId() != null) {
            positionOpt = runnerRepository.findPositionById(transaction.getTargetLotId());
        }

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

    private void releaseMargin(Transaction transaction) {
        TransactionStatus status = transaction.getStatus();
        if (!status.isFinal() || status == TransactionStatus.FILLED) {
            throw new IllegalArgumentException(
                    "releaseMargin requires a failed terminal status, got: " + status);
        }

        BigDecimal reserved = transaction.getTotal()
                .multiply(SAFETY_BUFFER)
                .setScale(8, RoundingMode.HALF_UP);

        buildMarginRelease(transaction, reserved, status).ifPresent(release -> {
            releaseMarginService.release(release);
            log.info("handleOrderTermination: margin release published transactionId={} amount={} reason={}",
                    transaction.getId(), release.releaseAmount(), release.reason());
        });
    }

    private Optional<MarginRelease> buildMarginRelease(Transaction transaction, BigDecimal reserved,
                                                       TransactionStatus status) {
        if (status == TransactionStatus.REJECTED || status == TransactionStatus.EXPIRED) {
            return Optional.of(MarginRelease.fullRelease(
                    transaction.getId(),
                    transaction.getRunnerId(),
                    reserved,
                    mapToReleaseReason(status)
            ));
        }

        // CANCELED
        BigDecimal executedValue = transaction.getExecutedValue();
        BigDecimal toRelease = reserved.subtract(executedValue)
                .setScale(8, RoundingMode.HALF_UP);

        if (toRelease.compareTo(BigDecimal.ZERO) <= 0) {
            log.info("handleOrderTermination: no margin to release for CANCELED " +
                            "transactionId={} (reserved={} executedValue={})",
                    transaction.getId(), reserved, executedValue);
            return Optional.empty();
        }

        return Optional.of(MarginRelease.partialRelease(
                transaction.getId(),
                transaction.getRunnerId(),
                toRelease,
                executedValue
        ));
    }

    private ReleaseReason mapToReleaseReason(TransactionStatus status) {
        return switch (status) {
            case REJECTED -> ReleaseReason.REJECTED;
            case EXPIRED  -> ReleaseReason.EXPIRED;
            case CANCELED -> ReleaseReason.CANCELED;
            default -> throw new IllegalArgumentException("Cannot map to ReleaseReason: " + status);
        };
    }
}
