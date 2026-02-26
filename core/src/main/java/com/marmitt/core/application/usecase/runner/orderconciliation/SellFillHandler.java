package com.marmitt.core.application.usecase.runner.orderconciliation;

import com.marmitt.core.domain.runner.Position;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.domain.runner.TransactionMatch;
import com.marmitt.core.domain.shared.Fee;
import com.marmitt.core.dto.capital.ExecutionConfirmation;
import com.marmitt.core.dto.events.ExecutionConfirmedEvent;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.enums.FeeType;
import com.marmitt.core.ports.outbound.events.EventPublisherPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.UUID;

/**
 * Aplica o efeito de um fill de venda: reduz a posicao, registra o resultado
 * da operacao e publica o evento de confirmacao de execucao para o sistema de P&L.
 *
 * <p>Um fill SELL aciona a seguinte sequencia:
 * <ol>
 *   <li><b>Resolucao da posicao alvo:</b> usa o {@code targetLotId} da transacao se
 *       disponivel (venda de lote especifico), ou localiza a posicao aberta do runner
 *       para o simbolo (politica implicita).</li>
 *   <li><b>Reducao da posicao:</b> decrementa a quantidade da posicao pelo
 *       {@code fillIncrement} e atualiza o preco medio. O valor da fee e subtraido
 *       do retorno liquido da venda.</li>
 *   <li><b>Unlock no fill final:</b> se o fill e terminal ({@code isFinal = true}),
 *       a posicao e desbloqueada — ela pode ter nova SELL emitida ou ser encerrada
 *       completamente.</li>
 *   <li><b>Criacao do {@link com.marmitt.core.domain.runner.TransactionMatch}:</b>
 *       registra o par BUY↔SELL com preco de compra, preco de venda, quantidade e fee,
 *       calculando o PnL realizado da operacao.</li>
 *   <li><b>Publicacao do {@link com.marmitt.core.dto.events.ExecutionConfirmedEvent}:</b>
 *       notifica o sistema de capital para liberar ou contabilizar o resultado no
 *       {@link com.marmitt.core.domain.portfolio.GlobalBalance}.</li>
 * </ol>
 *
 * <p><b>Caminho degradado:</b> se a posicao nao tiver {@code openedByTransactionId}
 * (posicao criada antes do rastreamento de origem ser implementado), o
 * {@code TransactionMatch} nao e criado e o evento nao e publicado. A posicao
 * e transacao sao persistidas normalmente, mas o P&L nao e contabilizado.
 */
@Slf4j
class SellFillHandler {

    private final StrategyRunnerRepositoryPort strategyRunnerRepository;
    private final EventPublisherPort eventPublisher;

    public SellFillHandler(StrategyRunnerRepositoryPort strategyRunnerRepository,
                           EventPublisherPort eventPublisher) {
        this.strategyRunnerRepository = strategyRunnerRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Aplica o fill SELL na posicao, cria o match e publica o evento de confirmacao.
     *
     * @param transaction   transacao SELL no estado SUBMITTED ou PARTIAL
     * @param orderData     evento original da exchange com fee e simbolo
     * @param fillIncrement quantidade executada neste evento (marginal, nao cumulativa)
     * @param fillPrice     preco medio ponderado do incremento calculado por {@link FillCalculator}
     * @param isFinal       {@code true} se este e o ultimo fill da ordem (FILLED),
     *                      {@code false} se e uma execucao parcial (PARTIALLY_FILLED)
     * @throws IllegalStateException se nenhuma posicao aberta for encontrada para a venda
     */
    public void handle(Transaction transaction, OrderDataDto orderData,
                       BigDecimal fillIncrement, BigDecimal fillPrice, boolean isFinal) {
        Position position = resolvePosition(transaction)
                .orElseThrow(() -> new IllegalStateException(
                        "No position found for SELL fill transactionId=" + transaction.getId()));

        BigDecimal buyPrice = position.getAveragePrice();
        UUID buyTransactionId = position.getOpenedByTransactionId();

        String quoteAsset = orderData.symbol().getQuoteAsset();
        Fee fee = buildFee(orderData.fee(), quoteAsset);

        position.reduceQuantity(fillIncrement, fillPrice, fee.getConvertedAmountOrZero());

        if (isFinal) {
            position.unlockAfterFill();
            log.debug("orderConciliation: position unlocked after final SELL fill positionId={} transactionId={}",
                    position.getId(), transaction.getId());
        }

        if (buyTransactionId == null) {
            log.warn("orderConciliation: position has no openedByTransactionId positionId={} " +
                    "- TransactionMatch not created for transactionId={}", position.getId(), transaction.getId());
            strategyRunnerRepository.savePosition(position);
            strategyRunnerRepository.saveTransaction(transaction);
            return;
        }

        TransactionMatch match = TransactionMatch.create(
                transaction.getRunnerId(),
                buyTransactionId,
                transaction.getId(),
                fillIncrement,
                buyPrice,
                fillPrice,
                fee
        );

        BigDecimal totalCost = fillIncrement.multiply(buyPrice).setScale(8, RoundingMode.HALF_UP);

        ExecutionConfirmation confirmation = new ExecutionConfirmation(
                transaction.getId(),
                transaction.getRunnerId(),
                match.id(),
                fillIncrement,
                fillPrice,
                fee,
                totalCost,
                match.pnlRealized(),
                isFinal
        );

        strategyRunnerRepository.saveAtomicTransactionAndMatch(transaction, match);
        strategyRunnerRepository.savePosition(position);
        eventPublisher.publishEvent(new ExecutionConfirmedEvent(confirmation));
        log.info("orderConciliation: SELL fill matchId={} runnerId={} increment={} pnl={}",
                match.id(), transaction.getRunnerId(), fillIncrement, match.pnlRealized());
    }

    /**
     * Resolve a posicao alvo da venda usando o mesmo criterio do pipeline de sinais:
     * lote especifico via {@code targetLotId} tem precedencia; fallback para a posicao
     * aberta do runner no simbolo (politica FIFO implicita).
     */
    private Optional<Position> resolvePosition(Transaction transaction) {
        return Optional.ofNullable(transaction.getTargetLotId())
                .flatMap(strategyRunnerRepository::findPositionById)
                .or(() -> strategyRunnerRepository.findOpenPositionByRunnerIdAndSymbol(
                        transaction.getRunnerId(), transaction.getSymbol()));
    }

    private Fee buildFee(BigDecimal feeAmount, String quoteAsset) {
        String asset = quoteAsset != null ? quoteAsset : "UNKNOWN";
        if (feeAmount == null || feeAmount.compareTo(BigDecimal.ZERO) == 0) {
            return Fee.zero(asset);
        }
        return Fee.sameBaseCurrency(feeAmount, asset, FeeType.UNKNOWN);
    }
}
