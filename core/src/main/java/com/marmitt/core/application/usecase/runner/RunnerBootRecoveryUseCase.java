package com.marmitt.core.application.usecase.runner.bootrecovery;

import com.marmitt.core.domain.Symbol;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.runner.RecoveryContext;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.enums.RunnerStatus;
import com.marmitt.core.enums.TransactionStatus;
import com.marmitt.core.ports.inbound.runner.OrderConciliationPort;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeAccountQueryPort;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeOrderQueryPort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Boot recovery executavel por runner.
 *
 * <p>Fluxo linear e explicito (IG 6.6 / 9.6):
 * <ul>
 *   <li>Step 0: snapshot de conta (observabilidade)</li>
 *   <li>Step 1: carregar transacoes em voo e classificar zombies/limbo</li>
 *   <li>Step 2: resolver capacidade de query na exchange (quando houver limbo)</li>
 *   <li>Step 3: saneamento de zombies</li>
 *   <li>Step 4: reconciliacao do limbo com a exchange</li>
 *   <li>Step 5: validacao final de inflight remanescente</li>
 *   <li>Step 6: concluir reconcilicao ou halting por erro</li>
 * </ul>
 *
 * <p>Dependencia entre passos:
 * Step 2 depende de Step 1 (limbo identificado).
 * Steps 3/4 dependem de Step 1 e Step 2.
 * Steps 5/6 dependem da execucao completa de 3/4.
 */
@Slf4j
public class RunnerBootRecoveryUseCase {

    private static final List<TransactionStatus> BOOT_RELEVANT_STATUSES = List.of(
            TransactionStatus.PENDING,
            TransactionStatus.SUBMITTED,
            TransactionStatus.PARTIAL
    );

    private final StrategyRunnerRepositoryPort strategyRunnerRepository;
    private final ExchangeAdapterRepositoryPort exchangeAdapterRepository;
    private final OrderConciliationPort orderConciliationPort;

    public RunnerBootRecoveryUseCase(StrategyRunnerRepositoryPort strategyRunnerRepository,
                                     ExchangeAdapterRepositoryPort exchangeAdapterRepository,
                                     OrderConciliationPort orderConciliationPort) {
        this.strategyRunnerRepository = strategyRunnerRepository;
        this.exchangeAdapterRepository = exchangeAdapterRepository;
        this.orderConciliationPort = orderConciliationPort;
    }

    public RecoverySummary recoverRunner(StrategyRunner runner) {
        RecoveryContext ctx = new RecoveryContext(runner);

        log.info("bootRecovery: start runnerId={} exchange={} status={} reconciling={}",
                ctx.runnerId(), ctx.exchangeId(), ctx.runner().getStatus(), ctx.runner().isReconciling());

        step0CaptureAccountSnapshot(ctx);
        step1LoadAndClassifyInFlight(ctx);
        step2ResolveOrderQueryCapability(ctx);
        step3ExpireZombies(ctx);
        step4ReconcileLimbo(ctx);
        step5ValidateRemainingInFlight(ctx);
        step6FinalizeRunnerState(ctx);

        log.info("bootRecovery: completed runnerId={} inFlight={} zombies={} limbo={} remaining={} hasErrors={}",
                ctx.runnerId(),
                ctx.inFlight().size(),
                ctx.zombies().size(),
                ctx.limbo().size(),
                ctx.remainingInFlight(),
                ctx.hasErrors());

        return new RecoverySummary(
                ctx.runnerId(),
                ctx.inFlight().size(),
                ctx.zombies().size(),
                ctx.limbo().size(),
                ctx.notes()
        );
    }

    private void step0CaptureAccountSnapshot(RecoveryContext ctx) {
        Optional<ExchangeAccountQueryPort> accountQueryOptional =
                exchangeAdapterRepository.findAccountQueryByName(ctx.exchangeId());
        if (accountQueryOptional.isEmpty()) {
            ctx.note("Step 0: account snapshot capability not available for exchange=" + ctx.exchangeId());
            return;
        }

        try {
            var account = accountQueryOptional.get().queryAccountSnapshot();
            ctx.note("Step 0: account snapshot captured exchange=" + ctx.exchangeId()
                    + " assets=" + account.balances().size());
        } catch (UnsupportedOperationException e) {
            ctx.note("Step 0: account snapshot unsupported for exchange=" + ctx.exchangeId());
        } catch (Exception e) {
            ctx.note("Step 0 WARN: account snapshot failed exchange=" + ctx.exchangeId()
                    + " reason=" + e.getMessage());
            log.warn("bootRecovery: account snapshot failed exchange={} runnerId={} reason={}",
                    ctx.exchangeId(), ctx.runnerId(), e.getMessage());
        }
    }

    private void step1LoadAndClassifyInFlight(RecoveryContext ctx) {
        List<Transaction> inFlight = strategyRunnerRepository.findByRunnerIdAndStatuses(
                ctx.runnerId(), BOOT_RELEVANT_STATUSES);
        ctx.inFlight(inFlight);

        List<Transaction> zombies = inFlight.stream()
                .filter(tx -> tx.getStatus() == TransactionStatus.PENDING)
                .filter(tx -> tx.getExchangeOrderId() == null || tx.getExchangeOrderId().isBlank())
                .toList();
        ctx.zombies(zombies);

        List<Transaction> limbo = inFlight.stream()
                .filter(tx -> !zombies.contains(tx))
                .toList();
        ctx.limbo(limbo);

        ctx.note("Step 1: classified inFlight=" + inFlight.size()
                + " zombies=" + zombies.size() + " limbo=" + limbo.size());
    }

    private void step2ResolveOrderQueryCapability(RecoveryContext ctx) {
        if (ctx.limbo().isEmpty()) {
            ctx.note("Step 2: no limbo transactions - exchange query not required.");
            return;
        }

        Optional<ExchangeOrderQueryPort> orderQueryOptional =
                exchangeAdapterRepository.findOrderQueryByName(ctx.exchangeId());
        if (orderQueryOptional.isEmpty()) {
            ctx.error("Step 2 ERROR: exchange does not expose order query capability exchange=" + ctx.exchangeId());
            log.warn("bootRecovery: missing order query capability exchange={} runnerId={}",
                    ctx.exchangeId(), ctx.runnerId());
            return;
        }

        ctx.orderQuery(orderQueryOptional.get());
        ctx.note("Step 2: order query capability resolved for exchange=" + ctx.exchangeId());
    }

    private void step3ExpireZombies(RecoveryContext ctx) {
        for (Transaction tx : ctx.zombies()) {
            try {
                OrderDataDto syntheticExpired = buildSyntheticTerminalOrder(
                        tx, OrderDataDto.OrderStatus.EXPIRED, "BOOT_ZOMBIE_PENDING_WITHOUT_EXCHANGE_ORDER_ID");
                orderConciliationPort.execute(syntheticExpired);
                ctx.note("Step 3: zombie expired transactionId=" + tx.getId());
            } catch (Exception e) {
                ctx.error("Step 3 ERROR: zombie transactionId=" + tx.getId() + " reason=" + e.getMessage());
                log.error("bootRecovery: failed to expire zombie transactionId={} runnerId={}",
                        tx.getId(), ctx.runnerId(), e);
            }
        }
    }

    private void step4ReconcileLimbo(RecoveryContext ctx) {
        if (ctx.limbo().isEmpty()) {
            ctx.note("Step 4: no limbo transactions to reconcile.");
            return;
        }
        if (ctx.orderQuery() == null) {
            ctx.error("Step 4 ERROR: limbo exists but order query capability is unavailable.");
            return;
        }

        for (Transaction tx : ctx.limbo()) {
            try {
                Optional<OrderDataDto> queried = ctx.orderQuery()
                        .queryOrderByClientOrderId(tx.getSymbol(), tx.getClientOrderId());

                if (queried.isPresent()) {
                    OrderDataDto normalized = normalizeQueriedOrder(tx, queried.get());
                    orderConciliationPort.execute(normalized);
                    ctx.note("Step 4: reconciled from exchange transactionId=" + tx.getId()
                            + " status=" + normalized.status());
                    continue;
                }

                OrderDataDto.OrderStatus fallbackStatus = tx.getStatus() == TransactionStatus.PARTIAL
                        ? OrderDataDto.OrderStatus.CANCELED
                        : OrderDataDto.OrderStatus.EXPIRED;
                OrderDataDto synthetic = buildSyntheticTerminalOrder(
                        tx, fallbackStatus, "BOOT_NOT_FOUND_ON_EXCHANGE");
                orderConciliationPort.execute(synthetic);
                ctx.note("Step 4: exchange not found -> local " + fallbackStatus
                        + " transactionId=" + tx.getId());

            } catch (UnsupportedOperationException e) {
                ctx.error("Step 4 ERROR: exchange query unsupported exchange=" + ctx.exchangeId()
                        + " transactionId=" + tx.getId());
                log.warn("bootRecovery: order query unsupported exchange={} runnerId={} transactionId={}",
                        ctx.exchangeId(), ctx.runnerId(), tx.getId());
                break;
            } catch (Exception e) {
                ctx.error("Step 4 ERROR: transactionId=" + tx.getId() + " reason=" + e.getMessage());
                log.error("bootRecovery: failed to reconcile limbo transactionId={} runnerId={}",
                        tx.getId(), ctx.runnerId(), e);
            }
        }
    }

    private void step5ValidateRemainingInFlight(RecoveryContext ctx) {
        int remaining = strategyRunnerRepository
                .findByRunnerIdAndStatuses(ctx.runnerId(), BOOT_RELEVANT_STATUSES)
                .size();
        ctx.remainingInFlight(remaining);
        ctx.note("Step 5: remainingInFlight=" + remaining);
    }

    private void step6FinalizeRunnerState(RecoveryContext ctx) {
        if (!ctx.hasErrors()) {
            ctx.runner().completeReconciliation();
            strategyRunnerRepository.save(ctx.runner());
            ctx.note("Step 6: reconciliation completed and runner persisted.");
            return;
        }

        if (ctx.runner().getStatus() == RunnerStatus.ACTIVE) {
            ctx.runner().halt();
            strategyRunnerRepository.save(ctx.runner());
            ctx.note("Step 6: runner moved ACTIVE->HALTED due to reconciliation errors.");
            return;
        }

        ctx.note("Step 6: reconciliation NOT completed due to previous errors.");
    }

    private OrderDataDto normalizeQueriedOrder(Transaction tx, OrderDataDto queried) {
        Symbol symbol = queried.symbol() != null ? queried.symbol() : Symbol.of(tx.getSymbol());
        OrderDataDto.OrderSide side = queried.side() != null
                ? queried.side()
                : (tx.isBuy() ? OrderDataDto.OrderSide.BUY : OrderDataDto.OrderSide.SELL);
        OrderDataDto.OrderType type = queried.type() != null
                ? queried.type()
                : OrderDataDto.OrderType.LIMIT;

        BigDecimal quantity = queried.quantity() != null ? queried.quantity() : tx.getQuantity();
        BigDecimal executedQty = queried.executedQuantity() != null
                ? queried.executedQuantity()
                : tx.getEffectiveExecutedQuantity();
        BigDecimal price = queried.price() != null ? queried.price() : tx.getPrice();
        BigDecimal executedPrice = queried.executedPrice() != null
                ? queried.executedPrice()
                : tx.getEffectiveExecutedPrice();
        BigDecimal fee = queried.fee() != null ? queried.fee() : BigDecimal.ZERO;

        return new OrderDataDto(
                queried.orderId() != null ? queried.orderId() : tx.getExchangeOrderId(),
                queried.clientOrderId() != null ? queried.clientOrderId() : tx.getClientOrderId(),
                symbol,
                side,
                type,
                quantity,
                executedQty,
                price,
                executedPrice,
                fee,
                queried.status(),
                queried.rejectReason(),
                queried.timestamp() != null ? queried.timestamp() : Instant.now()
        );
    }

    private OrderDataDto buildSyntheticTerminalOrder(Transaction tx,
                                                     OrderDataDto.OrderStatus status,
                                                     String reason) {
        return new OrderDataDto(
                tx.getExchangeOrderId() != null ? tx.getExchangeOrderId() : "BOOT_" + tx.getId(),
                tx.getClientOrderId(),
                Symbol.of(tx.getSymbol()),
                tx.isBuy() ? OrderDataDto.OrderSide.BUY : OrderDataDto.OrderSide.SELL,
                OrderDataDto.OrderType.LIMIT,
                tx.getQuantity(),
                tx.getEffectiveExecutedQuantity(),
                tx.getPrice(),
                tx.getEffectiveExecutedPrice(),
                BigDecimal.ZERO,
                status,
                reason,
                Instant.now()
        );
    }

    public record RecoverySummary(
            UUID runnerId,
            int inFlightCount,
            int zombiesCount,
            int limboCount,
            List<String> notes
    ) {}
}
