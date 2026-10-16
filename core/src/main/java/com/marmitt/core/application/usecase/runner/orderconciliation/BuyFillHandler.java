package com.marmitt.core.application.usecase.runner.orderconciliation;

import com.marmitt.core.domain.runner.Position;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.capital.MarginRelease;
import com.marmitt.core.dto.events.MarginReleaseEvent;
import com.marmitt.core.ports.outbound.events.EventPublisherPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;

/**
 * Aplica o efeito de um fill de compra na posicao do runner.
 *
 * <p>Cada fill BUY representa a execucao (total ou parcial) de uma ordem de compra
 * pela exchange. O handler atualiza a posicao local para refletir o ativo adquirido:
 * <ul>
 *   <li><b>Primeira execucao:</b> nenhuma posicao aberta existe para o runner/simbolo.
 *       Uma nova {@link com.marmitt.core.domain.runner.Position} e criada com a quantidade
 *       e preco do fill, e associada a transacao BUY de origem via
 *       {@code associateBuyTransaction} — vinculo necessario para criar o
 *       {@link com.marmitt.core.domain.runner.TransactionMatch} quando a posicao for
 *       eventualmente vendida.</li>
 *   <li><b>Fill adicional (PARTIALLY_FILLED → FILLED):</b> posicao aberta ja existe.
 *       A quantidade e o preco medio ponderado sao acumulados via {@code addQuantity}.</li>
 * </ul>
 *
 * <p>Posicao e transacao sao persistidas juntas ao final — ambas precisam refletir
 * o mesmo incremento de execucao para manter consistencia no relatorio de P&L.
 *
 * <p><b>Fill final (FILLED):</b> publica a devolucao do excedente da reserva
 * ({@link MarginReleaseBuilder#buildFilledExcess}) na mesma unidade transacional do fill, para
 * que {@code reserved} passe a refletir apenas o custo executado (IG §7.2.3, T36).
 */
@Slf4j
class BuyFillHandler {

    private final StrategyRunnerRepositoryPort strategyRunnerRepository;
    private final EventPublisherPort eventPublisher;
    private final MarginReleaseBuilder marginReleaseBuilder;

    public BuyFillHandler(StrategyRunnerRepositoryPort strategyRunnerRepository,
                          EventPublisherPort eventPublisher,
                          MarginReleaseBuilder marginReleaseBuilder) {
        this.strategyRunnerRepository = strategyRunnerRepository;
        this.eventPublisher = eventPublisher;
        this.marginReleaseBuilder = marginReleaseBuilder;
    }

    /**
     * Aplica o incremento de fill BUY na posicao e persiste transacao e posicao.
     *
     * @param transaction   transacao BUY no estado SUBMITTED ou PARTIAL
     * @param fillIncrement quantidade executada neste evento (marginal, nao cumulativa)
     * @param fillPrice     preco medio ponderado do incremento calculado por {@link FillCalculator}
     */
    public void handle(Transaction transaction, BigDecimal fillIncrement, BigDecimal fillPrice) {
        applyToPosition(transaction, fillIncrement, fillPrice);
        marginReleaseBuilder.buildFilledExcess(transaction)
                .ifPresent(release -> publishExcessRelease(transaction, release));
    }

    private void publishExcessRelease(Transaction transaction, MarginRelease release) {
        eventPublisher.publishEvent(new MarginReleaseEvent(release));
        log.info("orderConciliation: reserved excess release published transactionId={} amount={} executedValue={}",
                transaction.getId(), release.releaseAmount(), release.executedAmount());
    }

    private void applyToPosition(Transaction transaction, BigDecimal fillIncrement, BigDecimal fillPrice) {
        log.debug("orderConciliation: BUY fill start transactionId={} clientOrderId={} increment={} fillPrice={}",
                transaction.getId(), transaction.getClientOrderId(), fillIncrement, fillPrice);

        Position position = strategyRunnerRepository
                .findPositionByOpenedByTransactionIdForUpdate(transaction.getId())
                .orElse(null);

        if (position != null) {
            log.debug("orderConciliation: position found by openedByTransactionId positionId={} - adding quantity for transactionId={}",
                    position.getId(), transaction.getId());
            position.addQuantity(fillIncrement, fillPrice);
            strategyRunnerRepository.savePosition(position);
            strategyRunnerRepository.saveTransaction(transaction);
            log.info("orderConciliation: BUY fill positionId={} runnerId={} increment={} fillPrice={}",
                    position.getId(), transaction.getRunnerId(), fillIncrement, fillPrice);
            return;
        }

        position = strategyRunnerRepository
                .findOpenPositionByRunnerIdAndSymbolForUpdate(transaction.getRunnerId(), transaction.getSymbol())
                .orElse(null);

        if (position == null) {
            log.debug("orderConciliation: no open position found - creating new position for transactionId={}",
                    transaction.getId());
            position = new Position(transaction.getRunnerId(), transaction.getSymbol(),
                    fillIncrement, fillPrice);
            position.associateBuyTransaction(transaction.getId());
            boolean inserted = strategyRunnerRepository.trySavePosition(position);
            if (!inserted) {
                log.debug("orderConciliation: concurrent open position detected - reloading for transactionId={}",
                        transaction.getId());
                position = strategyRunnerRepository
                        .findPositionByOpenedByTransactionIdForUpdate(transaction.getId())
                        .orElseGet(() -> strategyRunnerRepository
                                .findOpenPositionByRunnerIdAndSymbolForUpdate(transaction.getRunnerId(), transaction.getSymbol())
                                .orElse(null));
                if (position == null) {
                    throw new IllegalStateException("Open position not found after unique constraint conflict");
                }
                position.addQuantity(fillIncrement, fillPrice);
                strategyRunnerRepository.savePosition(position);
            }
        } else {
            log.debug("orderConciliation: open position found positionId={} - adding quantity for transactionId={}",
                    position.getId(), transaction.getId());
            position.addQuantity(fillIncrement, fillPrice);
            strategyRunnerRepository.savePosition(position);
        }
        strategyRunnerRepository.saveTransaction(transaction);
        log.info("orderConciliation: BUY fill positionId={} runnerId={} increment={} fillPrice={}",
                position.getId(), transaction.getRunnerId(), fillIncrement, fillPrice);
    }
}
