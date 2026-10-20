package com.marmitt.core.application.usecase.runner.processsignal;

import com.marmitt.core.domain.runner.LiquidationPolicy;
import com.marmitt.core.domain.runner.Position;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.strategy.StrategyOutputDto;
import com.marmitt.core.enums.OrderType;
import com.marmitt.core.enums.TransactionStatus;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Acoes do Safe Mode sobre um runner nos niveis {@code CANCEL_ALL} e {@code PANIC_SELL} (T45).
 *
 * <p>Executado pelo {@link ProcessTradeSignalUseCase} no lugar da estrategia, a cada tick do runner.
 * Cada tick reaplica o nivel sobre o estado atual, o que torna a execucao convergente e idempotente:
 * <ul>
 *   <li>ordens LIMIT em voo recebem cancelamento (reenvio deduplicado pelo dispatch);</li>
 *   <li>em {@code PANIC_SELL}, cada posicao aberta sem lock vira um sinal de venda do lote inteiro, que
 *       segue o pipeline normal de SELL (normalizacao, lock, persistencia, dispatch). Posicao travada
 *       por uma SELL LIMIT so e vendida depois que o cancelamento a destrava; posicao travada pela
 *       propria liquidacao nao e vendida de novo.</li>
 * </ul>
 * Ordens MARKET nao sao canceladas: executam de imediato e sao a propria liquidacao.
 *
 * <p><b>Politica de liquidacao (T48).</b> Com {@link LiquidationPolicy} MARKET (default), o lote sai a
 * mercado. Com LIMIT, o lote recebe uma unica tentativa SELL LIMIT abaixo do preco do tick; sem
 * preenchimento em {@code escalationTicks} ticks ela e cancelada e, destravado o lote, o restante sai a
 * MARKET. A tentativa fica em memoria por lote porque a {@link Transaction} nao registra a origem da
 * ordem: apos um restart a LIMIT em voo e cancelada como qualquer outra e o lote ganha nova tentativa.
 */
@Slf4j
class SafeModeHandler {

    static final String STRATEGY_NAME = "SAFE_MODE";

    private final StrategyRunnerRepositoryPort strategyRunnerRepository;
    private final TradeIntentFactory intentFactory;
    private final CancelSignalHandler cancelSignalHandler;
    /** Tentativas LIMIT de liquidacao: runnerId → (lotId → ticks em que a LIMIT ficou em voo). */
    private final Map<UUID, Map<UUID, Integer>> limitAttempts = new ConcurrentHashMap<>();

    SafeModeHandler(StrategyRunnerRepositoryPort strategyRunnerRepository,
                    TradeIntentFactory intentFactory,
                    CancelSignalHandler cancelSignalHandler) {
        this.strategyRunnerRepository = strategyRunnerRepository;
        this.intentFactory = intentFactory;
        this.cancelSignalHandler = cancelSignalHandler;
    }

    /**
     * Envia cancelamento para cada ordem LIMIT em voo do runner, exceto a LIMIT de liquidacao ainda no
     * prazo (T48), que so e cancelada pela escalada. Falha em uma ordem e logada e nao impede as demais;
     * o proximo tick tenta de novo.
     */
    void cancelRestingOrders(StrategyRunner runner) {
        Map<UUID, Integer> attempts = limitAttempts.getOrDefault(runner.getId(), Map.of());
        for (Transaction transaction : strategyRunnerRepository.findByRunnerIdAndStatuses(
                runner.getId(), TransactionStatus.IN_FLIGHT)) {
            // SELL da estrategia pode nao ter lote alvo (FIFO); nem Map.of nem ConcurrentHashMap aceitam null.
            UUID targetLotId = transaction.getTargetLotId();
            if (transaction.isSell() && targetLotId != null && attempts.containsKey(targetLotId)) {
                continue;
            }
            cancelSignalHandler.cancelResting(runner, transaction, "safe-mode");
        }
    }

    /**
     * Vendas do lote inteiro para as posicoes abertas e sem lock do runner, no tipo de ordem que a
     * {@link LiquidationPolicy} do runner define para cada lote neste tick. Com LIMIT, conta o tick das
     * tentativas em voo e envia o cancelamento das que esgotaram o prazo.
     *
     * @param tickPrice preco do tick, base do preco da SELL LIMIT
     */
    List<Liquidation> liquidations(StrategyRunner runner, BigDecimal tickPrice) {
        // OPEN e CLOSING: o lote travado pela LIMIT de liquidacao fica CLOSING e precisa contar ticks.
        List<Position> activeLots = strategyRunnerRepository.findActivePositionsByPortfolioId(runner.getPortfolioId())
                .stream()
                .filter(position -> position.getRunnerId().equals(runner.getId()))
                .toList();
        LiquidationPolicy policy = runner.getLiquidationPolicy();
        if (!policy.usesLimit()) {
            forget(runner.getId());
            return activeLots.stream()
                    .filter(position -> !position.isLocked())
                    .map(position -> marketLiquidation(position, "safe-mode PANIC_SELL liquidation"))
                    .toList();
        }

        Map<UUID, Integer> attempts = limitAttempts.computeIfAbsent(runner.getId(), id -> new ConcurrentHashMap<>());
        Set<UUID> activeLotIds = activeLots.stream().map(Position::getId).collect(Collectors.toSet());
        attempts.keySet().retainAll(activeLotIds);

        List<Liquidation> liquidations = new ArrayList<>();
        for (Position position : activeLots) {
            Integer ticksInFlight = attempts.get(position.getId());
            if (position.isLocked()) {
                if (ticksInFlight != null) {
                    escalateWhenExpired(runner, position, ticksInFlight + 1, policy);
                    attempts.put(position.getId(), ticksInFlight + 1);
                }
            } else if (ticksInFlight != null) {
                liquidations.add(marketLiquidation(position, "safe-mode PANIC_SELL escalation to MARKET"));
            } else {
                attempts.put(position.getId(), 0);
                liquidations.add(new Liquidation(intentFactory.liquidationSignal(STRATEGY_NAME, position,
                        policy.limitPrice(tickPrice), "safe-mode PANIC_SELL LIMIT liquidation"), OrderType.LIMIT));
            }
        }
        return liquidations;
    }

    /**
     * Descarta as tentativas LIMIT do runner. Chamado fora do {@code PANIC_SELL}, para que um novo
     * {@code PANIC_SELL} comece com nova tentativa por lote.
     */
    void forget(UUID runnerId) {
        limitAttempts.remove(runnerId);
    }

    /**
     * Cancela a SELL LIMIT de liquidacao que trava o lote quando ela esgota o prazo. Reenviado a cada tick
     * ate o {@code CANCELED} destravar o lote (o dispatch deduplica). Lote travado por uma MARKET (a
     * propria escalada ou a protecao do lote) e ignorado por {@code cancelResting}.
     */
    private void escalateWhenExpired(StrategyRunner runner, Position position, int ticksInFlight,
                                     LiquidationPolicy policy) {
        if (ticksInFlight < policy.escalationTicks()) {
            return;
        }
        strategyRunnerRepository.findTransactionById(position.getLockedByTransactionId())
                .filter(transaction -> cancelSignalHandler.cancelResting(runner, transaction,
                        "safe-mode PANIC_SELL LIMIT unfilled after " + ticksInFlight + " ticks"))
                .ifPresent(transaction -> log.warn(
                        "safeMode: PANIC_SELL LIMIT unfilled - canceling to escalate to MARKET positionId={} runnerId={} transactionId={} ticks={}",
                        position.getId(), runner.getId(), transaction.getId(), ticksInFlight));
    }

    private Liquidation marketLiquidation(Position position, String reasoning) {
        return new Liquidation(intentFactory.liquidationSignal(STRATEGY_NAME, position, reasoning),
                intentFactory.liquidationOrderType());
    }

    /**
     * Venda de liquidacao de um lote e o tipo de ordem com que ela deve ser enviada.
     */
    record Liquidation(StrategyOutputDto signal, OrderType orderType) {
    }
}
