package com.marmitt.core.application.usecase.runner;

import com.marmitt.core.application.usecase.runner.orderconciliation.ConciliationOrderUpdateExecutor;
import com.marmitt.core.domain.Symbol;
import com.marmitt.core.domain.portfolio.DeadLetterEntry;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.runner.request.RecoverTransactionStatusRequest;
import com.marmitt.core.dto.runner.response.RecoverTransactionStatusResponse;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.enums.DlqReason;
import com.marmitt.core.enums.TransactionStatus;
import com.marmitt.core.exceptions.ExchangeQueryException;
import com.marmitt.core.ports.inbound.runner.RecoverTransactionStatusPort;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeOrderQueryPort;
import com.marmitt.core.ports.outbound.repository.DeadLetterEntryRepositoryPort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Slf4j
public class RecoverTransactionStatusUseCase implements RecoverTransactionStatusPort {

    private static final List<TransactionStatus> ELIGIBLE_STATUSES = List.of(
            TransactionStatus.PENDING,
            TransactionStatus.SUBMITTED,
            TransactionStatus.PARTIAL
    );

    private final StrategyRunnerRepositoryPort strategyRunnerRepository;
    private final ExchangeAdapterRepositoryPort exchangeAdapterRepository;
    private final DeadLetterEntryRepositoryPort deadLetterEntryRepository;
    private final ConciliationOrderUpdateExecutor conciliationOrderUpdateExecutor;

    public RecoverTransactionStatusUseCase(StrategyRunnerRepositoryPort strategyRunnerRepository,
                                           ExchangeAdapterRepositoryPort exchangeAdapterRepository,
                                           DeadLetterEntryRepositoryPort deadLetterEntryRepository,
                                           ConciliationOrderUpdateExecutor conciliationOrderUpdateExecutor) {
        this.strategyRunnerRepository = strategyRunnerRepository;
        this.exchangeAdapterRepository = exchangeAdapterRepository;
        this.deadLetterEntryRepository = deadLetterEntryRepository;
        this.conciliationOrderUpdateExecutor = conciliationOrderUpdateExecutor;
    }

    @Override
    public RecoverTransactionStatusResponse execute(RecoverTransactionStatusRequest request) {
        return execute(request, (orderQuery, transaction, runner) ->
                orderQuery.queryOrderByClientOrderId(transaction.getSymbol(), transaction.getClientOrderId()));
    }

    RecoverTransactionStatusResponse execute(RecoverTransactionStatusRequest request,
                                             OrderQueryOperation orderQueryOperation) {
        Transaction transaction = strategyRunnerRepository.findTransactionById(request.transactionId()).orElse(null);
        if (transaction == null) {
            return RecoverTransactionStatusResponse.failed(
                    request.transactionId(),
                    null,
                    null,
                    null,
                    RecoverTransactionStatusResponse.FailureReason.TRANSACTION_NOT_FOUND,
                    "Transaction not found."
            );
        }

        TransactionStatus statusBefore = transaction.getStatus();
        if (!ELIGIBLE_STATUSES.contains(statusBefore)) {
            return RecoverTransactionStatusResponse.skipped(
                    transaction.getId(),
                    transaction.getRunnerId(),
                    null,
                    statusBefore,
                    "Transaction status is not eligible for runtime recovery."
            );
        }

        StrategyRunner runner = strategyRunnerRepository.findById(transaction.getRunnerId()).orElse(null);
        if (runner == null) {
            return RecoverTransactionStatusResponse.failed(
                    transaction.getId(),
                    transaction.getRunnerId(),
                    null,
                    statusBefore,
                    RecoverTransactionStatusResponse.FailureReason.RUNNER_NOT_FOUND,
                    "Runner not found for transaction."
            );
        }

        String exchangeId = runner.getExchangeId();
        Optional<ExchangeOrderQueryPort> orderQueryOptional =
                exchangeAdapterRepository.findOrderQueryByName(exchangeId);
        if (orderQueryOptional.isEmpty()) {
            return RecoverTransactionStatusResponse.failed(
                    transaction.getId(),
                    transaction.getRunnerId(),
                    exchangeId,
                    statusBefore,
                    RecoverTransactionStatusResponse.FailureReason.ORDER_QUERY_NOT_AVAILABLE,
                    "Exchange order query capability is not available."
            );
        }

        Optional<OrderDataDto> queried;
        try {
            queried = orderQueryOperation.query(orderQueryOptional.get(), transaction, runner);
        } catch (UnsupportedOperationException e) {
            return failureFromQuery(
                    transaction,
                    exchangeId,
                    RecoverTransactionStatusResponse.FailureReason.ORDER_QUERY_UNSUPPORTED,
                    "Exchange order query is unsupported.",
                    e
            );
        } catch (ExchangeQueryException e) {
            if (e.errorType() == ExchangeQueryException.ErrorType.NOT_SUPPORTED) {
                return failureFromQuery(
                        transaction,
                        exchangeId,
                        RecoverTransactionStatusResponse.FailureReason.ORDER_QUERY_UNSUPPORTED,
                        "Exchange order query is unsupported.",
                        e
                );
            }
            return failureFromQuery(
                    transaction,
                    exchangeId,
                    isRetryableQueryFailure(e)
                            ? RecoverTransactionStatusResponse.FailureReason.EXCHANGE_QUERY_RETRYABLE_FAILURE
                            : RecoverTransactionStatusResponse.FailureReason.EXCHANGE_QUERY_TERMINAL_FAILURE,
                    e.getMessage(),
                    e
            );
        } catch (RuntimeException e) {
            return failureFromQuery(
                    transaction,
                    exchangeId,
                    isRetryableQueryFailure(e)
                            ? RecoverTransactionStatusResponse.FailureReason.EXCHANGE_QUERY_RETRYABLE_FAILURE
                            : RecoverTransactionStatusResponse.FailureReason.EXCHANGE_QUERY_TERMINAL_FAILURE,
                    e.getMessage(),
                    e
            );
        }

        if (queried.isPresent()) {
            OrderDataDto orderData = queried.get();
            if (orderData.status() == null) {
                return RecoverTransactionStatusResponse.failed(
                        transaction.getId(),
                        transaction.getRunnerId(),
                        exchangeId,
                        statusBefore,
                        RecoverTransactionStatusResponse.FailureReason.INVALID_EXCHANGE_RESPONSE,
                        "Exchange returned an order without status."
                );
            }
            String validationFailure = validateExchangeResponse(transaction, orderData);
            if (validationFailure != null) {
                return RecoverTransactionStatusResponse.failed(
                        transaction.getId(),
                        transaction.getRunnerId(),
                        exchangeId,
                        statusBefore,
                        RecoverTransactionStatusResponse.FailureReason.INVALID_EXCHANGE_RESPONSE,
                        validationFailure
                );
            }

            OrderDataDto normalized = normalizeQueriedOrder(transaction, orderData);
            try {
                conciliationOrderUpdateExecutor.execute(normalized);
            } catch (IllegalArgumentException | IllegalStateException e) {
                return RecoverTransactionStatusResponse.failed(
                        transaction.getId(),
                        transaction.getRunnerId(),
                        exchangeId,
                        statusBefore,
                        RecoverTransactionStatusResponse.FailureReason.INVALID_EXCHANGE_RESPONSE,
                        "Exchange response could not be reconciled: " + e.getMessage()
                );
            }
            return RecoverTransactionStatusResponse.recovered(
                    transaction.getId(),
                    transaction.getRunnerId(),
                    exchangeId,
                    statusBefore,
                    transaction.getStatus(),
                    RecoverTransactionStatusResponse.RecoveryAction.RECONCILED_FROM_EXCHANGE,
                    "Transaction reconciled from exchange status " + normalized.status() + "."
            );
        }

        return switch (request.missingOrderPolicy()) {
            case APPLY_TERMINAL_FALLBACK -> applyTerminalFallback(transaction, exchangeId, statusBefore);
            case REGISTER_DLQ -> routeMissingOrderToDlq(transaction, runner, exchangeId, statusBefore);
        };
    }

    private RecoverTransactionStatusResponse applyTerminalFallback(Transaction transaction,
                                                                   String exchangeId,
                                                                   TransactionStatus statusBefore) {
        OrderDataDto.OrderStatus fallbackStatus = transaction.getStatus() == TransactionStatus.PARTIAL
                ? OrderDataDto.OrderStatus.CANCELED
                : OrderDataDto.OrderStatus.EXPIRED;
        OrderDataDto synthetic = buildSyntheticTerminalOrder(transaction, fallbackStatus, "RUNTIME_NOT_FOUND_ON_EXCHANGE");
        conciliationOrderUpdateExecutor.execute(synthetic);

        RecoverTransactionStatusResponse.RecoveryAction action = fallbackStatus == OrderDataDto.OrderStatus.CANCELED
                ? RecoverTransactionStatusResponse.RecoveryAction.MARKED_CANCELED
                : RecoverTransactionStatusResponse.RecoveryAction.MARKED_EXPIRED;

        return RecoverTransactionStatusResponse.recovered(
                transaction.getId(),
                transaction.getRunnerId(),
                exchangeId,
                statusBefore,
                transaction.getStatus(),
                action,
                "Exchange did not find order; applied local " + fallbackStatus + " fallback."
        );
    }

    private RecoverTransactionStatusResponse routeMissingOrderToDlq(Transaction transaction,
                                                                    StrategyRunner runner,
                                                                    String exchangeId,
                                                                    TransactionStatus statusBefore) {
        boolean alreadyOpen = deadLetterEntryRepository.existsUnresolvedByIdentity(
                runner.getPortfolioId(),
                transaction.getRunnerId(),
                transaction.getClientOrderId(),
                transaction.getExchangeOrderId(),
                DlqReason.RECONCILIATION_CONFLICT
        );

        if (!alreadyOpen) {
            try {
                deadLetterEntryRepository.save(new DeadLetterEntry(
                        runner.getPortfolioId(),
                        transaction.getRunnerId(),
                        transaction.getClientOrderId(),
                        transaction.getExchangeOrderId(),
                        buildRuntimeNotFoundDlqPayload(transaction, exchangeId, statusBefore),
                        DlqReason.RECONCILIATION_CONFLICT
                ));
            } catch (RuntimeException e) {
                log.error("runtimeRecovery: failed to persist DLQ transactionId={} runnerId={} exchange={}",
                        transaction.getId(), transaction.getRunnerId(), exchangeId, e);
                return RecoverTransactionStatusResponse.failed(
                        transaction.getId(),
                        transaction.getRunnerId(),
                        exchangeId,
                        statusBefore,
                        RecoverTransactionStatusResponse.FailureReason.DLQ_PERSISTENCE_FAILURE,
                        "Failed to persist runtime recovery DLQ entry: " + e.getMessage()
                );
            }
        }

        return RecoverTransactionStatusResponse.recovered(
                transaction.getId(),
                transaction.getRunnerId(),
                exchangeId,
                statusBefore,
                transaction.getStatus(),
                RecoverTransactionStatusResponse.RecoveryAction.ROUTED_TO_DLQ,
                alreadyOpen
                        ? "Exchange did not find order; existing unresolved DLQ entry kept."
                        : "Exchange did not find order; transaction routed to DLQ."
        );
    }

    private String validateExchangeResponse(Transaction transaction, OrderDataDto orderData) {
        if (orderData.clientOrderId() != null
                && !orderData.clientOrderId().isBlank()
                && !transaction.getClientOrderId().equals(orderData.clientOrderId())) {
            return "Exchange returned a mismatched clientOrderId for the requested transaction.";
        }

        return switch (orderData.status()) {
            case NEW -> {
                String orderId = orderData.orderId() != null && !orderData.orderId().isBlank()
                        ? orderData.orderId()
                        : transaction.getExchangeOrderId();
                if (orderId == null || orderId.isBlank()) {
                    yield "Exchange returned NEW without orderId for reconciliation.";
                }
                yield null;
            }
            case FILLED, PARTIALLY_FILLED -> {
                if (orderData.executedQuantity() == null
                        || orderData.executedQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                    yield "Exchange returned fill status without a positive executedQuantity.";
                }
                if (orderData.executedPrice() == null
                        || orderData.executedPrice().compareTo(BigDecimal.ZERO) <= 0) {
                    yield "Exchange returned fill status without a positive executedPrice.";
                }
                yield null;
            }
            case REJECTED -> {
                if (orderData.rejectReason() == null || orderData.rejectReason().isBlank()) {
                    yield "Exchange returned REJECTED without rejectReason.";
                }
                yield null;
            }
            case CANCELED, EXPIRED -> null;
        };
    }

    private RecoverTransactionStatusResponse failureFromQuery(Transaction transaction,
                                                              String exchangeId,
                                                              RecoverTransactionStatusResponse.FailureReason reason,
                                                              String message,
                                                              Exception e) {
        log.warn("runtimeRecovery: failed transactionId={} runnerId={} exchange={} reason={} message={}",
                transaction.getId(), transaction.getRunnerId(), exchangeId, reason, message);
        log.debug("runtimeRecovery: query failure stack transactionId={}", transaction.getId(), e);
        return RecoverTransactionStatusResponse.failed(
                transaction.getId(),
                transaction.getRunnerId(),
                exchangeId,
                transaction.getStatus(),
                reason,
                message != null && !message.isBlank()
                        ? message
                        : "Exchange query failed."
        );
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
                tx.getClientOrderId(),
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
                tx.getExchangeOrderId() != null ? tx.getExchangeOrderId() : "RUNTIME_" + tx.getId(),
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

    private String buildRuntimeNotFoundDlqPayload(Transaction transaction,
                                                  String exchangeId,
                                                  TransactionStatus statusBefore) {
        return "source=runner.recovery.transaction"
                + ", exchange=" + exchangeId
                + ", runnerId=" + transaction.getRunnerId()
                + ", transactionId=" + transaction.getId()
                + ", clientOrderId=" + transaction.getClientOrderId()
                + ", exchangeOrderId=" + transaction.getExchangeOrderId()
                + ", symbol=" + transaction.getSymbol()
                + ", statusBefore=" + statusBefore
                + ", outcome=ORDER_NOT_FOUND_ON_EXCHANGE";
    }

    private boolean isRetryableQueryFailure(Throwable throwable) {
        if (throwable instanceof UnsupportedOperationException) {
            return false;
        }
        if (throwable instanceof IllegalArgumentException) {
            return false;
        }
        if (throwable instanceof ExchangeQueryException exchangeQueryException) {
            return exchangeQueryException.isRetryable();
        }

        String message = throwable.getMessage() != null
                ? throwable.getMessage().toLowerCase(Locale.ROOT)
                : "";

        if (message.contains("timeout")
                || message.contains("timed out")
                || message.contains("connection reset")
                || message.contains("temporarily")
                || message.contains("rate limit")
                || message.contains("429")
                || message.contains("503")) {
            return true;
        }

        Throwable cause = throwable.getCause();
        if (cause == null || cause == throwable) {
            return false;
        }
        return isRetryableQueryFailure(cause);
    }

    @FunctionalInterface
    interface OrderQueryOperation {
        Optional<OrderDataDto> query(ExchangeOrderQueryPort orderQuery,
                                     Transaction transaction,
                                     StrategyRunner runner);
    }
}
