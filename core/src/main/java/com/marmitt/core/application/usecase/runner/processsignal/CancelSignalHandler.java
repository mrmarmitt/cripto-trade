package com.marmitt.core.application.usecase.runner.processsignal;

import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.runner.OrderCancelCommand;
import com.marmitt.core.dto.strategy.StrategyOutputDto;
import com.marmitt.core.enums.TransactionStatus;
import com.marmitt.core.ports.outbound.exchange.OrderDispatchPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
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

    private static final List<TransactionStatus> CANCELABLE_STATUSES = List.of(
            TransactionStatus.PENDING,
            TransactionStatus.SUBMITTED,
            TransactionStatus.PARTIAL
    );

    private final StrategyRunnerRepositoryPort strategyRunnerRepository;
    private final OrderDispatchPort orderDispatch;

    CancelSignalHandler(StrategyRunnerRepositoryPort strategyRunnerRepository, OrderDispatchPort orderDispatch) {
        this.strategyRunnerRepository = strategyRunnerRepository;
        this.orderDispatch = orderDispatch;
    }

    void handle(StrategyRunner runner, StrategyOutputDto signal) {
        UUID targetTransactionId = signal.targetTransactionId();
        if (targetTransactionId == null) {
            log.warn("processCancelSignal: SHOULD_CANCEL without targetTransactionId runnerId={} - ignored",
                    runner.getId());
            return;
        }

        Transaction transaction = strategyRunnerRepository.findTransactionById(targetTransactionId).orElse(null);
        if (transaction == null) {
            log.warn("processCancelSignal: target transaction not found transactionId={} runnerId={} - ignored",
                    targetTransactionId, runner.getId());
            return;
        }

        if (!transaction.getRunnerId().equals(runner.getId())) {
            log.warn("processCancelSignal: target belongs to another runner transactionId={} owner={} requester={} - ignored",
                    targetTransactionId, transaction.getRunnerId(), runner.getId());
            return;
        }

        if (!CANCELABLE_STATUSES.contains(transaction.getStatus())) {
            log.info("processCancelSignal: target not cancelable transactionId={} status={} runnerId={} - no-op",
                    targetTransactionId, transaction.getStatus(), runner.getId());
            return;
        }

        // De-dup de reenvios (estrategia deterministica reemite SHOULD_CANCEL a cada tick enquanto o
        // CANCELED async nao chega) e tratado no OrderDispatchAdapter, que marca o cooldown apenas
        // apos um envio REAL (blocked/unsupported nao consomem a janela).
        orderDispatch.cancel(new OrderCancelCommand(
                transaction.getClientOrderId(), runner.getId(), runner.getSymbol(), runner.getExchangeId()));

        log.info("processCancelSignal: cancel dispatched transactionId={} clientOrderId={} runnerId={} reason={}",
                targetTransactionId, transaction.getClientOrderId(), runner.getId(), signal.reasoning());
    }
}
