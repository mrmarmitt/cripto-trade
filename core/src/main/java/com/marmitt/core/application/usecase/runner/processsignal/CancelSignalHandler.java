package com.marmitt.core.application.usecase.runner.processsignal;

import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.ports.outbound.exchange.OrderDispatchPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

/**
 * Executa o ramo {@code SHOULD_CANCEL} do pipeline de sinais: valida o alvo e despacha o
 * cancelamento (fire-and-forget) via {@link OrderDispatchPort#cancel}.
 *
 * <p><b>Rejeicao silenciosa:</b> o contexto da estrategia e montado no inicio do tick e pode
 * estar levemente defasado (a ordem pode ter enchido ou sumido nesse meio-tempo). Alvo
 * inexistente, de outro runner ou ja terminal e tratado como no-op logado — nunca excecao que
 * derrube o tick.
 *
 * <p><b>Sem marcacao terminal otimista:</b> o handler nao altera o estado local. O {@code CANCELED}
 * real chega via stream e e conciliado pelo caminho idempotente, liberando capital quando aplicavel.
 */
@Slf4j
class CancelSignalHandler {

    private final StrategyRunnerRepositoryPort strategyRunnerRepository;
    private final TradeIntentFactory intentFactory;
    private final OrderDispatchPort orderDispatch;

    CancelSignalHandler(StrategyRunnerRepositoryPort strategyRunnerRepository, TradeIntentFactory intentFactory,
                        OrderDispatchPort orderDispatch) {
        this.strategyRunnerRepository = strategyRunnerRepository;
        this.intentFactory = intentFactory;
        this.orderDispatch = orderDispatch;
    }

    /**
     * Valida o alvo e despacha o cancelamento. Usado pelo {@code SHOULD_CANCEL} da estrategia (que
     * ignora o desfecho) e pelo comando admin de cancelar ordem (T49), que o traduz em resposta.
     */
    CancelOutcome handle(StrategyRunner runner, UUID targetTransactionId, String reason) {
        if (targetTransactionId == null) {
            log.warn("processCancelSignal: cancel without targetTransactionId runnerId={} - ignored", runner.getId());
            return CancelOutcome.NOT_FOUND;
        }

        Transaction transaction = strategyRunnerRepository.findTransactionById(targetTransactionId).orElse(null);
        if (transaction == null) {
            log.warn("processCancelSignal: target transaction not found transactionId={} runnerId={} - ignored",
                    targetTransactionId, runner.getId());
            return CancelOutcome.NOT_FOUND;
        }

        if (!transaction.getRunnerId().equals(runner.getId())) {
            log.warn("processCancelSignal: target belongs to another runner transactionId={} owner={} requester={} - ignored",
                    targetTransactionId, transaction.getRunnerId(), runner.getId());
            return CancelOutcome.NOT_FOUND;
        }

        if (!transaction.getStatus().isInFlight()) {
            log.info("processCancelSignal: target not cancelable transactionId={} status={} runnerId={} - no-op",
                    targetTransactionId, transaction.getStatus(), runner.getId());
            return CancelOutcome.NOT_CANCELABLE;
        }

        // De-dup de reenvios (estrategia deterministica reemite SHOULD_CANCEL a cada tick enquanto o
        // CANCELED async nao chega) e tratado no OrderDispatchAdapter, que marca o cooldown apenas
        // apos um envio REAL (blocked/unsupported nao consomem a janela).
        orderDispatch.cancel(intentFactory.buildCancelCommand(runner, transaction));

        log.info("processCancelSignal: cancel dispatched transactionId={} clientOrderId={} runnerId={} reason={}",
                targetTransactionId, transaction.getClientOrderId(), runner.getId(), reason);
        return CancelOutcome.DISPATCHED;
    }

    /**
     * Desfecho da validacao do alvo. Alvo de outro runner conta como inexistente: o runner nao
     * enxerga ordens alheias.
     */
    enum CancelOutcome {
        DISPATCHED,
        NOT_FOUND,
        NOT_CANCELABLE
    }
}
