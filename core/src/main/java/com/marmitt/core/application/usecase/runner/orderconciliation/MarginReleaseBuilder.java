package com.marmitt.core.application.usecase.runner.orderconciliation;

import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.capital.MarginRelease;
import com.marmitt.core.enums.ReleaseReason;
import com.marmitt.core.enums.TransactionStatus;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Calcula o valor de margem a devolver ao portfolio quando uma transacao e encerrada
 * com falha, distinguindo tres comportamentos conforme o status terminal.
 *
 * <p><b>REJECTED / EXPIRED — liberacao total:</b> a ordem nunca foi executada.
 * Todo o valor reservado ({@code transaction.getTotal()}) e devolvido integralmente.
 *
 * <p><b>CANCELED — liberacao parcial:</b> a ordem pode ter sido parcialmente executada
 * antes do cancelamento. Apenas a diferenca entre o valor reservado e o valor ja executado
 * ({@code reserved - executedValue}) e devolvida. Se o valor executado ja consumiu toda
 * a reserva (cenario improvavel mas possivel em precos de mercado), nao ha margem a liberar
 * e o metodo retorna vazio.
 *
 * <p>O {@link com.marmitt.core.dto.capital.MarginRelease} resultante e publicado como
 * {@link com.marmitt.core.dto.events.MarginReleaseEvent} pelo {@link TerminationHandler}
 * para que o {@link com.marmitt.core.domain.portfolio.GlobalBalance} atualize o saldo
 * disponivel do portfolio.
 */
@Slf4j
class MarginReleaseBuilder {

    /**
     * Calcula a liberacao de margem para a transacao encerrada com falha.
     *
     * @param transaction transacao com status terminal (CANCELED, EXPIRED ou REJECTED)
     * @return {@link com.marmitt.core.dto.capital.MarginRelease} a publicar, ou vazio se
     *         nao ha saldo a devolver (CANCELED com execucao que consumiu toda a reserva)
     * @throws IllegalArgumentException se o status nao for um status de falha terminal
     */
    public Optional<MarginRelease> build(Transaction transaction) {
        TransactionStatus status = transaction.getStatus();
        if (!status.isFailed()) {
            throw new IllegalArgumentException(
                    "releaseMargin requires a failed terminal status, got: " + status);
        }

        BigDecimal reserved = transaction.getTotal();

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
