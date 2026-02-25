package com.marmitt.core.application.usecase.runner.orderconciliation;

import com.marmitt.core.domain.runner.Position;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.capital.MarginRelease;
import com.marmitt.core.dto.events.MarginReleaseEvent;
import com.marmitt.core.ports.outbound.events.EventPublisherPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * Encerra transacoes que falharam (CANCELED, EXPIRED ou REJECTED) e devolve
 * o capital reservado ao portfolio.
 *
 * <p>O encerramento segue uma sequencia deliberada:
 * <ol>
 *   <li><b>Unlock da posicao (somente SELL):</b> se a transacao era uma venda,
 *       a posicao alvo pode ter sido bloqueada pelo {@code SellSignalHandler} antes
 *       do dispatch. O lock e removido para que a posicao fique disponivel para uma
 *       nova tentativa de venda. O unlock so ocorre se a transacao falha realmente
 *       possui o lock — verificacao por {@code lockedByTransactionId} evita desbloquear
 *       uma posicao que ja foi relockada por outra transacao concorrente.</li>
 *   <li><b>Persistencia da transacao:</b> salva o status terminal (CANCELED/EXPIRED/REJECTED)
 *       e o motivo de rejeicao no banco.</li>
 *   <li><b>Liberacao de margem:</b> publica {@link com.marmitt.core.dto.events.MarginReleaseEvent}
 *       para que o {@link com.marmitt.core.domain.portfolio.GlobalBalance} devolva o
 *       capital que havia sido reservado no momento do BUY.</li>
 * </ol>
 *
 * <p>Transacoes SELL nao tem capital reservado proprio (o capital ja estava na posicao BUY),
 * mas o evento de liberacao ainda e relevante para contabilidade de margem bloqueada.
 *
 * @see MarginReleaseBuilder
 */
@Slf4j
class TerminationHandler {

    private final StrategyRunnerRepositoryPort strategyRunnerRepository;
    private final EventPublisherPort eventPublisher;
    private final MarginReleaseBuilder marginReleaseBuilder;

    public TerminationHandler(StrategyRunnerRepositoryPort strategyRunnerRepository,
                              EventPublisherPort eventPublisher,
                              MarginReleaseBuilder marginReleaseBuilder) {
        this.strategyRunnerRepository = strategyRunnerRepository;
        this.eventPublisher = eventPublisher;
        this.marginReleaseBuilder = marginReleaseBuilder;
    }

    /**
     * Executa o encerramento: unlock opcional de posicao, persistencia do status
     * terminal e publicacao do evento de liberacao de margem.
     *
     * @param transaction transacao ja com status terminal aplicado pelo chamador
     *                    (via {@code cancel()}, {@code expire()} ou {@code reject()})
     */
    public void handle(Transaction transaction) {
        log.debug("orderConciliation: processing transactionId={} status={}",
                transaction.getId(), transaction.getStatus());

        findAndUnlockPosition(transaction)
                .ifPresent(strategyRunnerRepository::savePosition);
        strategyRunnerRepository.saveTransaction(transaction);

        marginReleaseBuilder.build(transaction)
                .ifPresent(release -> publishMarginRelease(transaction, release));
    }

    /**
     * Localiza e desbloqueia a posicao de uma SELL que falhou, se aplicavel.
     * Ignorado para transacoes BUY — elas nao bloqueiam posicoes.
     * O desbloqueio e condicional: so ocorre se o lock pertence a esta transacao,
     * evitando race condition com SELLs concorrentes.
     */
    private Optional<Position> findAndUnlockPosition(Transaction transaction) {
        if (!transaction.isSell()) {
            return Optional.empty();
        }

        return resolvePosition(transaction)
                .filter(position -> transaction.getId().equals(position.getLockedByTransactionId()))
                .map(position -> {
                    position.releaseFromFailedSell();
                    log.debug("orderConciliation: position unlocked positionId={} transactionId={}",
                            position.getId(), transaction.getId());
                    return position;
                });
    }

    private Optional<Position> resolvePosition(Transaction transaction) {
        return Optional.ofNullable(transaction.getTargetLotId())
                .flatMap(strategyRunnerRepository::findPositionById)
                .or(() -> strategyRunnerRepository.findOpenPositionByRunnerIdAndSymbol(
                        transaction.getRunnerId(), transaction.getSymbol()));
    }

    private void publishMarginRelease(Transaction transaction, MarginRelease release) {
        eventPublisher.publishEvent(new MarginReleaseEvent(release));
        log.info("orderConciliation: margin release published transactionId={} amount={} reason={}",
                transaction.getId(), release.releaseAmount(), release.reason());
    }
}
