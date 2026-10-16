package com.marmitt.core.application.usecase.runner.orderconciliation;

import com.marmitt.core.domain.runner.Position;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.capital.MarginRelease;
import com.marmitt.core.dto.events.MarginReleaseEvent;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
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
 *       Uma nova {@link com.marmitt.core.domain.runner.Position} e aberta com a quantidade
 *       recebida e o custo pago, e associada a transacao BUY de origem via
 *       {@code associateBuyTransaction} — vinculo necessario para criar o
 *       {@link com.marmitt.core.domain.runner.TransactionMatch} quando a posicao for
 *       eventualmente vendida.</li>
 *   <li><b>Fill adicional (PARTIALLY_FILLED → FILLED):</b> posicao aberta ja existe.
 *       Quantidade e custo sao acumulados via {@code addQuantity}; o preco medio deriva do custo.</li>
 * </ul>
 *
 * <p>Posicao e transacao sao persistidas juntas ao final — ambas precisam refletir
 * o mesmo incremento de execucao para manter consistencia no relatorio de P&L.
 *
 * <p><b>Fee (T46):</b> a posicao registra a quantidade efetivamente recebida e o custo em quote
 * efetivamente pago. Fee no ativo base (padrao da Binance para BUY) reduz a quantidade recebida e,
 * com o mesmo custo, eleva o preco medio — sem isso o lote ficaria maior que o saldo real e a venda
 * do lote inteiro seria rejeitada pela exchange. Fee em quote ou em terceiro ativo (ex.: BNB) nao e
 * suportada para BUY nesta versao: e ignorada e registrada em log ERROR (decisao da T46).
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
    private final FeeResolver feeResolver;

    public BuyFillHandler(StrategyRunnerRepositoryPort strategyRunnerRepository,
                          EventPublisherPort eventPublisher,
                          MarginReleaseBuilder marginReleaseBuilder,
                          FeeResolver feeResolver) {
        this.strategyRunnerRepository = strategyRunnerRepository;
        this.eventPublisher = eventPublisher;
        this.marginReleaseBuilder = marginReleaseBuilder;
        this.feeResolver = feeResolver;
    }

    /**
     * Aplica o incremento de fill BUY na posicao e persiste transacao e posicao.
     *
     * @param transaction   transacao BUY no estado SUBMITTED ou PARTIAL
     * @param orderData     evento original da exchange (fee e ativo da fee deste incremento)
     * @param fillIncrement quantidade executada neste evento (marginal, nao cumulativa)
     * @param cost          custo em quote do incremento ({@link FillCalculator.FillComputation#costIncrement})
     */
    public void handle(Transaction transaction, OrderDataDto orderData,
                       BigDecimal fillIncrement, BigDecimal cost) {
        BigDecimal received = receivedQuantity(transaction, orderData, fillIncrement);
        applyToPosition(transaction, received, cost);
        marginReleaseBuilder.buildFilledExcess(transaction)
                .ifPresent(release -> publishExcessRelease(transaction, release));
    }

    /**
     * Quantidade efetivamente recebida no incremento: o executado menos a fee quando ela e cobrada
     * no ativo base. Demais casos mantem o executado (comportamento legado) e registram o motivo.
     */
    private BigDecimal receivedQuantity(Transaction transaction, OrderDataDto orderData, BigDecimal fillIncrement) {
        FeeResolver.ResolvedFee fee = feeResolver.resolve(orderData);
        switch (fee.kind()) {
            case BASE -> {
                BigDecimal received = fillIncrement.subtract(fee.amount());
                if (received.signum() > 0) {
                    return received;
                }
                log.error("orderConciliation: BUY fee in base asset >= executed increment transactionId={} increment={} fee={} - fee ignored",
                        transaction.getId(), fillIncrement, fee.amount());
            }
            case QUOTE, UNSUPPORTED -> log.error(
                    "orderConciliation: unsupported BUY fee asset transactionId={} fee={} asset={} - fee not accounted (T46)",
                    transaction.getId(), fee.amount(), fee.asset());
            case UNKNOWN -> log.warn(
                    "orderConciliation: BUY fee without asset transactionId={} fee={} - fee not accounted",
                    transaction.getId(), fee.amount());
            case NONE -> { }
        }
        return fillIncrement;
    }

    private void publishExcessRelease(Transaction transaction, MarginRelease release) {
        eventPublisher.publishEvent(new MarginReleaseEvent(release));
        log.info("orderConciliation: reserved excess release published transactionId={} amount={} executedValue={}",
                transaction.getId(), release.releaseAmount(), release.executedAmount());
    }

    private void applyToPosition(Transaction transaction, BigDecimal received, BigDecimal cost) {
        log.debug("orderConciliation: BUY fill start transactionId={} clientOrderId={} received={} cost={}",
                transaction.getId(), transaction.getClientOrderId(), received, cost);

        Position position = strategyRunnerRepository
                .findPositionByOpenedByTransactionIdForUpdate(transaction.getId())
                .orElse(null);

        if (position != null) {
            log.debug("orderConciliation: position found by openedByTransactionId positionId={} - adding quantity for transactionId={}",
                    position.getId(), transaction.getId());
            position.addQuantity(received, cost);
            strategyRunnerRepository.savePosition(position);
            strategyRunnerRepository.saveTransaction(transaction);
            log.info("orderConciliation: BUY fill positionId={} runnerId={} received={} cost={}",
                    position.getId(), transaction.getRunnerId(), received, cost);
            return;
        }

        position = strategyRunnerRepository
                .findOpenPositionByRunnerIdAndSymbolForUpdate(transaction.getRunnerId(), transaction.getSymbol())
                .orElse(null);

        if (position == null) {
            log.debug("orderConciliation: no open position found - creating new position for transactionId={}",
                    transaction.getId());
            position = Position.open(transaction.getRunnerId(), transaction.getSymbol(),
                    received, cost);
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
                position.addQuantity(received, cost);
                strategyRunnerRepository.savePosition(position);
            }
        } else {
            log.debug("orderConciliation: open position found positionId={} - adding quantity for transactionId={}",
                    position.getId(), transaction.getId());
            position.addQuantity(received, cost);
            strategyRunnerRepository.savePosition(position);
        }
        strategyRunnerRepository.saveTransaction(transaction);
        log.info("orderConciliation: BUY fill positionId={} runnerId={} received={} cost={}",
                position.getId(), transaction.getRunnerId(), received, cost);
    }
}
