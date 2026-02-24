package com.marmitt.core.application.usecase.runner;

import com.marmitt.core.domain.runner.ClientOrderId;
import com.marmitt.core.domain.runner.Position;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.domain.runner.TransactionMatch;
import com.marmitt.core.domain.shared.Fee;
import com.marmitt.core.dto.capital.ExecutionConfirmation;
import com.marmitt.core.dto.capital.MarginRelease;
import com.marmitt.core.dto.events.ExecutionConfirmedEvent;
import com.marmitt.core.dto.events.MarginReleaseEvent;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.enums.FeeType;
import com.marmitt.core.enums.ReleaseReason;
import com.marmitt.core.enums.TransactionStatus;
import com.marmitt.core.ports.inbound.runner.OrderConciliationPort;
import com.marmitt.core.ports.outbound.events.EventPublisherPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.UUID;

@Slf4j
public abstract class OrderConciliationUseCase implements OrderConciliationPort {

    private final StrategyRunnerRepositoryPort strategyRunnerRepository;
    private final EventPublisherPort eventPublisher;

    public OrderConciliationUseCase(StrategyRunnerRepositoryPort strategyRunnerRepository,
                                    EventPublisherPort eventPublisher) {
        this.strategyRunnerRepository = strategyRunnerRepository;
        this.eventPublisher = eventPublisher;
    }

    public void execute(OrderDataDto orderData) {
        String clientOrderId = orderData.clientOrderId();

        if (!ClientOrderId.isValid(clientOrderId)) {
            log.trace("orderConciliation: clientOrderId={} not in v1 format — skipping", clientOrderId);
            return;
        }

        Transaction transaction = strategyRunnerRepository
                .findTransactionByClientOrderId(clientOrderId)
                .orElse(null);

        if (transaction == null) {
            log.debug("orderConciliation: transaction not found for clientOrderId={} — skipping", clientOrderId);
            return;
        }

        log.debug("orderConciliation: routing clientOrderId={} transactionId={} status={}",
                clientOrderId, transaction.getId(), orderData.status());

        switch (orderData.status()) {
            case NEW -> {
                transaction.submit(orderData.orderId());
                transactionalSubmit(transaction);
            }
            case FILLED -> {
                BigDecimal prevQty = transaction.getEffectiveExecutedQuantity();
                BigDecimal prevPrice = transaction.getEffectiveExecutedPrice();
                transaction.fill(orderData.executedQuantity(), orderData.executedPrice());
                BigDecimal fillIncrement = transaction.getExecutedQuantity().subtract(prevQty);
                BigDecimal fillPrice = computeFillPrice(prevQty, prevPrice,
                        transaction.getExecutedQuantity(), transaction.getExecutedPrice(), fillIncrement);
                transactionalProcessFill(transaction, orderData, fillIncrement, fillPrice, true);
            }
            case PARTIALLY_FILLED -> {
                BigDecimal prevQty = transaction.getEffectiveExecutedQuantity();
                BigDecimal prevPrice = transaction.getEffectiveExecutedPrice();
                transaction.partialFill(orderData.executedQuantity(), orderData.executedPrice());
                BigDecimal fillIncrement = transaction.getExecutedQuantity().subtract(prevQty);
                BigDecimal fillPrice = computeFillPrice(prevQty, prevPrice,
                        transaction.getExecutedQuantity(), transaction.getExecutedPrice(), fillIncrement);
                transactionalProcessFill(transaction, orderData, fillIncrement, fillPrice, false);
            }
            case CANCELED -> {
                transaction.cancel();
                transactionalReleaseMargin(transaction);
            }
            case EXPIRED -> {
                transaction.expire();
                transactionalReleaseMargin(transaction);
            }
            case REJECTED -> {
                transaction.reject(orderData.rejectReason());
                transactionalReleaseMargin(transaction);
            }
            default -> log.warn("orderConciliation: unexpected status={} for clientOrderId={}",
                    orderData.status(), clientOrderId);
        }
    }

    public abstract void transactionalSubmit(Transaction transaction);

    public abstract void transactionalProcessFill(Transaction transaction, OrderDataDto orderData,
                                                  BigDecimal fillIncrement, BigDecimal fillPrice,
                                                  boolean isFinal);

    protected void submitTransaction(Transaction transaction) {
        strategyRunnerRepository.saveTransaction(transaction);
        log.info("orderConciliation: PENDING→SUBMITTED transactionId={} exchangeOrderId={}",
                transaction.getId(), transaction.getExchangeOrderId());
    }

    protected void processFill(Transaction transaction, OrderDataDto orderData,
                                BigDecimal fillIncrement, BigDecimal fillPrice, boolean isFinal) {
        if (transaction.isBuy()) {
            processBuyFill(transaction, fillIncrement, fillPrice);
        } else {
            processSellFill(transaction, orderData, fillIncrement, fillPrice, isFinal);
        }
    }

    private void processBuyFill(Transaction transaction, BigDecimal fillIncrement, BigDecimal fillPrice) {
        Position position = strategyRunnerRepository
                .findOpenPositionByRunnerIdAndSymbol(transaction.getRunnerId(), transaction.getSymbol())
                .orElse(null);

        if (position == null) {
            position = new Position(transaction.getRunnerId(), transaction.getSymbol(),
                    fillIncrement, fillPrice);
            position.associateBuyTransaction(transaction.getId());
        } else {
            position.addQuantity(fillIncrement, fillPrice);
        }

        strategyRunnerRepository.savePosition(position);
        strategyRunnerRepository.saveTransaction(transaction);
        log.info("orderConciliation: BUY fill positionId={} runnerId={} increment={} fillPrice={}",
                position.getId(), transaction.getRunnerId(), fillIncrement, fillPrice);
    }

    private void processSellFill(Transaction transaction, OrderDataDto orderData,
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
        }

        if (buyTransactionId == null) {
            log.warn("orderConciliation: position has no openedByTransactionId positionId={} " +
                    "— TransactionMatch not created for transactionId={}", position.getId(), transaction.getId());
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
                isFinal
        );

        strategyRunnerRepository.saveAtomicTransactionAndMatch(transaction, match);
        strategyRunnerRepository.savePosition(position);
        eventPublisher.publishEvent(new ExecutionConfirmedEvent(confirmation));
        log.info("orderConciliation: SELL fill matchId={} runnerId={} increment={} pnl={}",
                match.id(), transaction.getRunnerId(), fillIncrement, match.pnlRealized());
    }

    private Fee buildFee(BigDecimal feeAmount, String quoteAsset) {
        String asset = quoteAsset != null ? quoteAsset : "UNKNOWN";
        if (feeAmount == null || feeAmount.compareTo(BigDecimal.ZERO) == 0) {
            return Fee.zero(asset);
        }
        return Fee.sameBaseCurrency(feeAmount, asset, FeeType.UNKNOWN);
    }

    private static BigDecimal computeFillPrice(BigDecimal prevQty, BigDecimal prevWap,
                                               BigDecimal newQty, BigDecimal newWap,
                                               BigDecimal increment) {
        if (prevQty.compareTo(BigDecimal.ZERO) == 0) {
            return newWap;
        }
        BigDecimal newValue = newQty.multiply(newWap);
        BigDecimal prevValue = prevQty.multiply(prevWap);
        return newValue.subtract(prevValue).divide(increment, 8, RoundingMode.HALF_UP);
    }

    public void releaseMargin(Transaction transaction) {
        log.debug("orderConciliation: processing transactionId={} status={}",
                transaction.getId(), transaction.getStatus());

        findAndUnlockPosition(transaction)
                .ifPresent(strategyRunnerRepository::savePosition);
        strategyRunnerRepository.saveTransaction(transaction);
        transactionReleaseMargin(transaction);
    }

    private Optional<Position> findAndUnlockPosition(Transaction transaction) {
        if (!transaction.isSell()) {
            return Optional.empty();
        }

        return resolvePosition(transaction)
                .filter(position -> transaction.getId().equals(position.getLockedByTransactionId()))
                .map(position -> {
                    position.releaseFromFailedSell();
                    log.debug("orderConciliation: position unlocked positionId={} transactionId={}",
                            position.getId(), transaction.getId());
                    return position;
                });
    }

    private Optional<Position> resolvePosition(Transaction transaction) {
        return Optional.ofNullable(transaction.getTargetLotId())
                .flatMap(strategyRunnerRepository::findPositionById)
                .or(() -> strategyRunnerRepository.findOpenPositionByRunnerIdAndSymbol(
                        transaction.getRunnerId(), transaction.getSymbol()));
    }

    private void transactionReleaseMargin(Transaction transaction) {
        TransactionStatus status = transaction.getStatus();
        if (!status.isFailed()) {
            throw new IllegalArgumentException(
                    "releaseMargin requires a failed terminal status, got: " + status);
        }

        BigDecimal reserved = transaction.getTotal();

        buildMarginRelease(transaction, reserved, status)
                .ifPresent(release -> {
                    eventPublisher.publishEvent(new MarginReleaseEvent(release));
                    log.info("orderConciliation: margin release published transactionId={} amount={} reason={}",
                            transaction.getId(), release.releaseAmount(), release.reason());
                });
    }

    private Optional<MarginRelease> buildMarginRelease(Transaction transaction, BigDecimal reserved,
                                                       TransactionStatus status) {
        if (status == TransactionStatus.REJECTED || status == TransactionStatus.EXPIRED) {
            return Optional.of(MarginRelease.fullRelease(
                    transaction.getId(),
                    transaction.getRunnerId(),
                    reserved,
                    mapToReleaseReason(status)
            ));
        }

        // CANCELED
        BigDecimal executedValue = transaction.getExecutedValue();
        BigDecimal toRelease = reserved.subtract(executedValue)
                .setScale(8, RoundingMode.HALF_UP);

        if (toRelease.compareTo(BigDecimal.ZERO) <= 0) {
            log.info("handleOrderTermination: no margin to release for CANCELED " +
                            "transactionId={} (reserved={} executedValue={})",
                    transaction.getId(), reserved, executedValue);
            return Optional.empty();
        }

        return Optional.of(MarginRelease.partialRelease(
                transaction.getId(),
                transaction.getRunnerId(),
                toRelease,
                executedValue
        ));
    }

    private ReleaseReason mapToReleaseReason(TransactionStatus status) {
        return switch (status) {
            case REJECTED -> ReleaseReason.REJECTED;
            case EXPIRED  -> ReleaseReason.EXPIRED;
            case CANCELED -> ReleaseReason.CANCELED;
            default -> throw new IllegalArgumentException("Cannot map to ReleaseReason: " + status);
        };
    }
}
