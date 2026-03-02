package com.marmitt.core.application.usecase.runner.bootrecovery;

import com.marmitt.core.domain.Symbol;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.domain.runner.Transaction;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Boot recovery executavel por runner.
 *
 * <p>Implementa o fluxo base do IG 6.6/9.6:
 * <ul>
 *   <li>Step 1: saneamento de zumbis (PENDING sem exchangeOrderId).</li>
 *   <li>Step 2/3: reconciliacao do limbo via consulta autoritativa na exchange.</li>
 *   <li>Step 4: aplica transicoes locais delegando ao OrderConciliationPort.</li>
 *   <li>Step 5/6: valida remanescente e marca reconciliacao concluida.</li>
 * </ul>
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
        UUID runnerId = runner.getId();
        List<String> notes = new ArrayList<>();
        boolean hasErrors = false;

        log.info("bootRecovery: start runnerId={} exchange={} status={} reconciling={}",
                runnerId, runner.getExchangeId(), runner.getStatus(), runner.isReconciling());

        // Step 0: snapshot opcional de conta para observabilidade.
        captureAccountSnapshot(runner, notes);

        // Preparacao: carregar transacoes em voo.
        List<Transaction> inFlight = strategyRunnerRepository.findByRunnerIdAndStatuses(
                runnerId, BOOT_RELEVANT_STATUSES);

        // Step 1: zumbis = PENDING sem exchangeOrderId.
        List<Transaction> zombies = inFlight.stream()
                .filter(tx -> tx.getStatus() == TransactionStatus.PENDING)
                .filter(tx -> tx.getExchangeOrderId() == null || tx.getExchangeOrderId().isBlank())
                .toList();

        for (Transaction tx : zombies) {
            try {
                OrderDataDto syntheticExpired = buildSyntheticTerminalOrder(
                        tx, OrderDataDto.OrderStatus.EXPIRED, "BOOT_ZOMBIE_PENDING_WITHOUT_EXCHANGE_ORDER_ID");
                orderConciliationPort.execute(syntheticExpired);
                notes.add("Step 1: zombie expired transactionId=" + tx.getId());
            } catch (Exception e) {
                hasErrors = true;
                notes.add("Step 1 ERROR: zombie transactionId=" + tx.getId() + " reason=" + e.getMessage());
                log.error("bootRecovery: failed to expire zombie transactionId={} runnerId={}",
                        tx.getId(), runnerId, e);
            }
        }

        // Step 2: limbo = restante (PENDING com exchangeOrderId + SUBMITTED + PARTIAL).
        List<Transaction> limbo = inFlight.stream()
                .filter(tx -> !zombies.contains(tx))
                .toList();

        if (!limbo.isEmpty()) {
            Optional<ExchangeOrderQueryPort> orderQueryOptional =
                    exchangeAdapterRepository.findOrderQueryByName(runner.getExchangeId());

            if (orderQueryOptional.isEmpty()) {
                hasErrors = true;
                String msg = "Step 2/3 ERROR: exchange does not expose order query capability exchange="
                        + runner.getExchangeId();
                notes.add(msg);
                log.warn("bootRecovery: {} runnerId={}", msg, runnerId);
            } else {
                ExchangeOrderQueryPort orderQuery = orderQueryOptional.get();
                hasErrors = reconcileLimboTransactions(runner, limbo, orderQuery, notes) || hasErrors;
            }
        }

        // Step 5: validar remanescente.
        int remainingInFlight = strategyRunnerRepository
                .findByRunnerIdAndStatuses(runnerId, BOOT_RELEVANT_STATUSES)
                .size();
        notes.add("Step 5: remainingInFlight=" + remainingInFlight);

        // Step 6: concluir reconciliacao local quando nao houve erro.
        if (!hasErrors) {
            runner.completeReconciliation();
            strategyRunnerRepository.save(runner);
            notes.add("Step 6: reconciliation completed and runner persisted.");
        } else {
            if (runner.getStatus() == RunnerStatus.ACTIVE) {
                runner.halt();
                strategyRunnerRepository.save(runner);
                notes.add("Step 6: runner moved ACTIVE->HALTED due to reconciliation errors.");
            } else {
                notes.add("Step 6: reconciliation NOT completed due to previous errors.");
            }
        }

        log.info("bootRecovery: completed runnerId={} inFlight={} zombies={} limbo={} remaining={} hasErrors={}",
                runnerId, inFlight.size(), zombies.size(), limbo.size(), remainingInFlight, hasErrors);

        return new RecoverySummary(runnerId, inFlight.size(), zombies.size(), limbo.size(), notes);
    }

    private boolean reconcileLimboTransactions(StrategyRunner runner,
                                               List<Transaction> limbo,
                                               ExchangeOrderQueryPort orderQuery,
                                               List<String> notes) {
        boolean hasErrors = false;

        for (Transaction tx : limbo) {
            try {
                Optional<OrderDataDto> queried = orderQuery.queryOrderByClientOrderId(
                        tx.getSymbol(), tx.getClientOrderId());

                if (queried.isPresent()) {
                    OrderDataDto normalized = normalizeQueriedOrder(tx, queried.get());
                    orderConciliationPort.execute(normalized);
                    notes.add("Step 3/4: reconciled from exchange transactionId=" + tx.getId()
                            + " status=" + normalized.status());
                    continue;
                }

                OrderDataDto.OrderStatus fallbackStatus = tx.getStatus() == TransactionStatus.PARTIAL
                        ? OrderDataDto.OrderStatus.CANCELED
                        : OrderDataDto.OrderStatus.EXPIRED;

                OrderDataDto synthetic = buildSyntheticTerminalOrder(
                        tx, fallbackStatus, "BOOT_NOT_FOUND_ON_EXCHANGE");
                orderConciliationPort.execute(synthetic);
                notes.add("Step 3/4: exchange not found -> local " + fallbackStatus
                        + " transactionId=" + tx.getId());

            } catch (UnsupportedOperationException e) {
                hasErrors = true;
                notes.add("Step 3 ERROR: exchange query unsupported for exchange="
                        + runner.getExchangeId() + " transactionId=" + tx.getId());
                log.warn("bootRecovery: order query unsupported exchange={} runnerId={} transactionId={}",
                        runner.getExchangeId(), runner.getId(), tx.getId());
                break;
            } catch (Exception e) {
                hasErrors = true;
                notes.add("Step 3/4 ERROR: transactionId=" + tx.getId() + " reason=" + e.getMessage());
                log.error("bootRecovery: failed to reconcile limbo transactionId={} runnerId={}",
                        tx.getId(), runner.getId(), e);
            }
        }

        return hasErrors;
    }

    private void captureAccountSnapshot(StrategyRunner runner, List<String> notes) {
        Optional<ExchangeAccountQueryPort> accountQueryOptional =
                exchangeAdapterRepository.findAccountQueryByName(runner.getExchangeId());
        if (accountQueryOptional.isEmpty()) {
            notes.add("Step 0: account snapshot capability not available for exchange=" + runner.getExchangeId());
            return;
        }

        try {
            var account = accountQueryOptional.get().queryAccountSnapshot();
            notes.add("Step 0: account snapshot captured exchange=" + runner.getExchangeId()
                    + " assets=" + account.balances().size());
        } catch (UnsupportedOperationException e) {
            notes.add("Step 0: account snapshot unsupported for exchange=" + runner.getExchangeId());
        } catch (Exception e) {
            notes.add("Step 0 WARN: account snapshot failed exchange=" + runner.getExchangeId()
                    + " reason=" + e.getMessage());
            log.warn("bootRecovery: account snapshot failed exchange={} runnerId={} reason={}",
                    runner.getExchangeId(), runner.getId(), e.getMessage());
        }
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
        BigDecimal executedQty = tx.getEffectiveExecutedQuantity();
        BigDecimal executedPrice = tx.getEffectiveExecutedPrice();

        return new OrderDataDto(
                tx.getExchangeOrderId() != null ? tx.getExchangeOrderId() : "BOOT_" + tx.getId(),
                tx.getClientOrderId(),
                Symbol.of(tx.getSymbol()),
                tx.isBuy() ? OrderDataDto.OrderSide.BUY : OrderDataDto.OrderSide.SELL,
                OrderDataDto.OrderType.LIMIT,
                tx.getQuantity(),
                executedQty,
                tx.getPrice(),
                executedPrice,
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
