package com.marmitt.core.application.usecase.runner.orderconciliation;

import com.marmitt.core.domain.runner.Position;
import com.marmitt.core.domain.runner.Transaction;
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
 */
@Slf4j
class BuyFillHandler {

    private final StrategyRunnerRepositoryPort strategyRunnerRepository;

    public BuyFillHandler(StrategyRunnerRepositoryPort strategyRunnerRepository) {
        this.strategyRunnerRepository = strategyRunnerRepository;
    }

    /**
     * Aplica o incremento de fill BUY na posicao e persiste transacao e posicao.
     *
     * @param transaction   transacao BUY no estado SUBMITTED ou PARTIAL
     * @param fillIncrement quantidade executada neste evento (marginal, nao cumulativa)
     * @param fillPrice     preco medio ponderado do incremento calculado por {@link FillCalculator}
     */
    public void handle(Transaction transaction, BigDecimal fillIncrement, BigDecimal fillPrice) {
        log.debug("orderConciliation: BUY fill start transactionId={} clientOrderId={} increment={} fillPrice={}",
                transaction.getId(), transaction.getClientOrderId(), fillIncrement, fillPrice);

        Position position = strategyRunnerRepository
                .findPositionByOpenedByTransactionId(transaction.getId())
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
                .findOpenPositionByRunnerIdAndSymbol(transaction.getRunnerId(), transaction.getSymbol())
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
                        .findPositionByOpenedByTransactionId(transaction.getId())
                        .orElseGet(() -> strategyRunnerRepository
                                .findOpenPositionByRunnerIdAndSymbol(transaction.getRunnerId(), transaction.getSymbol())
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
