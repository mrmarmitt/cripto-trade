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
 * <p><b>FILLED (BUY) — devolucao do excedente:</b> ver {@link #buildFilledExcess(Transaction)}.
 * A reserva e feita a {@code quantity x preco-limite}, mas a baixa na venda usa o preco executado;
 * sem esta devolucao, a diferenca ficaria presa em {@code reserved} para sempre (IG §7.2.3, T36).
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

        // SELL nao reserva quote (bloqueia uma Position; o capital ja estava no BUY). Terminar
        // uma SELL deve apenas desbloquear a Position (no TerminationHandler), NUNCA liberar quote
        // — senao subtrairia do reservedBalance, que pertence a BUYs nao relacionados.
        if (transaction.isSell()) {
            return Optional.empty();
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

    /**
     * Calcula a devolucao do excedente de uma BUY totalmente executada.
     *
     * <p>Usa a mesma formula do CANCELED parcial ({@code reservado - valorExecutado}): o que a BUY
     * nao consumiu volta ao disponivel, e o que ficou em {@code reserved} passa a ser exatamente o
     * custo executado — o mesmo valor que as vendas daquele lote baixam em {@code confirmExecution}.
     * A chave de idempotencia continua sendo o {@code transactionId}; como FILLED e CANCELED sao
     * terminais mutuamente exclusivos, uma transacao nunca gera duas devolucoes.
     *
     * @param transaction transacao recem-finalizada pelo fill
     * @return devolucao a publicar, ou vazio para SELL, para status diferente de FILLED ou quando
     *         a execucao consumiu toda a reserva
     */
    public Optional<MarginRelease> buildFilledExcess(Transaction transaction) {
        if (transaction.isSell() || transaction.getStatus() != TransactionStatus.FILLED) {
            return Optional.empty();
        }
        BigDecimal reserved = transaction.getTotal();
        BigDecimal executedValue = transaction.getExecutedValue();
        BigDecimal excess = reserved.subtract(executedValue).setScale(8, RoundingMode.HALF_UP);
        if (excess.signum() < 0) {
            // LIMIT nao executa acima do limite; custo maior que a reserva indica reserva
            // insuficiente (ex.: futuro MARKET sem buffer). Nao ha o que devolver.
            log.warn("orderConciliation: executed value exceeds reservation transactionId={} reserved={} executedValue={}",
                    transaction.getId(), reserved, executedValue);
            return Optional.empty();
        }
        if (excess.signum() == 0) {
            return Optional.empty();
        }
        return Optional.of(MarginRelease.filledExcess(
                transaction.getId(),
                transaction.getRunnerId(),
                excess,
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
