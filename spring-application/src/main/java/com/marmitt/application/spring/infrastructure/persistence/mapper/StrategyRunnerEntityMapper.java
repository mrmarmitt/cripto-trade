package com.marmitt.application.spring.infrastructure.persistence.mapper;

import com.marmitt.application.spring.infrastructure.persistence.entity.RunnerMarketDataSourceEntity;
import com.marmitt.application.spring.infrastructure.persistence.entity.RunnerPositionEntity;
import com.marmitt.application.spring.infrastructure.persistence.entity.RunnerTransactionEntity;
import com.marmitt.application.spring.infrastructure.persistence.entity.RunnerTransactionMatchEntity;
import com.marmitt.application.spring.infrastructure.persistence.entity.StrategyRunnerEntity;
import com.marmitt.core.domain.runner.Position;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.domain.runner.TransactionMatch;
import com.marmitt.core.domain.shared.Fee;
import com.marmitt.core.enums.FeeType;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

public class StrategyRunnerEntityMapper {

    // ── StrategyRunner ──────────────────────────────────────────────────────

    public static StrategyRunnerEntity toEntity(StrategyRunner domain) {
        return StrategyRunnerEntity.builder()
                .id(domain.getId())
                .portfolioId(domain.getPortfolioId())
                .shortCode(domain.getShortCode())
                .strategyId(domain.getStrategyId())
                .strategyName(domain.getStrategyName())
                .symbol(domain.getSymbol())
                .exchangeId(domain.getExchangeId())
                .allowedMarketDataSources(toSourceEntities(domain.getAllowedMarketDataSources()))
                .status(domain.getStatus())
                .executionPolicy(domain.getExecutionPolicy())
                .accountingPolicyType(domain.getAccountingPolicyType())
                .maxAllocationPercent(domain.getMaxAllocationPercent())
                .maxOpenPositions(domain.getMaxOpenPositions())
                .maxPendingOrders(domain.getMaxPendingOrders())
                .dedicatedBudget(domain.getDedicatedBudget())
                .isReconciling(domain.isReconciling())
                .createdAt(domain.getCreatedAt())
                .statusChangedAt(domain.getStatusChangedAt())
                .lastReconciliationAt(domain.getLastReconciliationAt())
                .archivedAt(domain.getArchivedAt())
                .version(domain.getVersion())
                .build();
    }

    public static StrategyRunner toDomain(StrategyRunnerEntity entity) {
        return new StrategyRunner(
                entity.getId(),
                entity.getPortfolioId(),
                entity.getShortCode(),
                entity.getStrategyId(),
                entity.getStrategyName(),
                entity.getSymbol(),
                entity.getExchangeId(),
                fromSourceEntities(entity.getAllowedMarketDataSources()),
                entity.getStatus(),
                entity.getExecutionPolicy(),
                entity.getAccountingPolicyType(),
                entity.getMaxAllocationPercent(),
                entity.getMaxOpenPositions(),
                entity.getMaxPendingOrders(),
                entity.getDedicatedBudget(),
                entity.isReconciling(),
                entity.getCreatedAt(),
                entity.getStatusChangedAt(),
                entity.getLastReconciliationAt(),
                entity.getArchivedAt(),
                entity.getVersion()
        );
    }

    // ── Position ────────────────────────────────────────────────────────────

    public static RunnerPositionEntity toEntity(Position domain) {
        return RunnerPositionEntity.builder()
                .id(domain.getId())
                .runnerId(domain.getRunnerId())
                .symbol(domain.getSymbol())
                .status(domain.getStatus())
                .quantity(domain.getQuantity())
                .averagePrice(domain.getAveragePrice())
                .currentPrice(domain.getCurrentPrice())
                .realizedPnl(domain.getRealizedPnl())
                .openedAt(domain.getOpenedAt())
                .closedAt(domain.getClosedAt())
                .updatedAt(domain.getUpdatedAt())
                .openedByTransactionId(domain.getOpenedByTransactionId())
                .lockedByTransactionId(domain.getLockedByTransactionId())
                .lockedQuantity(domain.getLockedQuantity())
                .lockedAt(domain.getLockedAt())
                .version(domain.getVersion())
                .build();
    }

    public static Position toDomain(RunnerPositionEntity entity) {
        return Position.reconstitute()
                .id(entity.getId())
                .runnerId(entity.getRunnerId())
                .symbol(entity.getSymbol())
                .status(entity.getStatus())
                .quantity(entity.getQuantity())
                .averagePrice(entity.getAveragePrice())
                .currentPrice(entity.getCurrentPrice())
                .realizedPnl(entity.getRealizedPnl())
                .openedAt(entity.getOpenedAt())
                .closedAt(entity.getClosedAt())
                .updatedAt(entity.getUpdatedAt())
                .lockedByTransactionId(entity.getLockedByTransactionId())
                .lockedQuantity(entity.getLockedQuantity())
                .lockedAt(entity.getLockedAt())
                .openedByTransactionId(entity.getOpenedByTransactionId())
                .version(entity.getVersion())
                .build();
    }

    // ── Transaction ─────────────────────────────────────────────────────────

    public static RunnerTransactionEntity toEntity(Transaction domain) {
        return RunnerTransactionEntity.builder()
                .id(domain.getId())
                .runnerId(domain.getRunnerId())
                .clientOrderId(domain.getClientOrderId())
                .exchangeOrderId(domain.getExchangeOrderId())
                .status(domain.getStatus())
                .type(domain.getType())
                .symbol(domain.getSymbol())
                .quantity(domain.getQuantity())
                .executedQuantity(domain.getExecutedQuantity())
                .price(domain.getPrice())
                .executedPrice(domain.getExecutedPrice())
                .total(domain.getTotal())
                .confidence(domain.getConfidence())
                .reasoning(domain.getReasoning())
                .targetLotId(domain.getTargetLotId())
                .requestedAt(domain.getRequestedAt())
                .updatedAt(domain.getUpdatedAt())
                .executedAt(domain.getExecutedAt())
                .rejectReason(domain.getRejectReason())
                .version(domain.getVersion())
                .build();
    }

    public static Transaction toDomain(RunnerTransactionEntity entity) {
        return Transaction.reconstitute()
                .id(entity.getId())
                .runnerId(entity.getRunnerId())
                .clientOrderId(entity.getClientOrderId())
                .exchangeOrderId(entity.getExchangeOrderId())
                .status(entity.getStatus())
                .type(entity.getType())
                .symbol(entity.getSymbol())
                .quantity(entity.getQuantity())
                .executedQuantity(entity.getExecutedQuantity())
                .price(entity.getPrice())
                .executedPrice(entity.getExecutedPrice())
                .total(entity.getTotal())
                .confidence(entity.getConfidence())
                .reasoning(entity.getReasoning())
                .targetLotId(entity.getTargetLotId())
                .requestedAt(entity.getRequestedAt())
                .updatedAt(entity.getUpdatedAt())
                .executedAt(entity.getExecutedAt())
                .rejectReason(entity.getRejectReason())
                .version(entity.getVersion())
                .build();
    }

    // ── TransactionMatch ─────────────────────────────────────────────────────

    public static RunnerTransactionMatchEntity toEntity(TransactionMatch domain) {
        Fee fee = domain.fee();
        return RunnerTransactionMatchEntity.builder()
                .id(domain.id())
                .runnerId(domain.runnerId())
                .buyTransactionId(domain.buyTransactionId())
                .sellTransactionId(domain.sellTransactionId())
                .matchedQuantity(domain.matchedQuantity())
                .buyPrice(domain.buyPrice())
                .sellPrice(domain.sellPrice())
                .pnlRealized(domain.pnlRealized())
                .feeAmount(fee.amount())
                .feeAsset(fee.asset())
                .feeType(fee.type())
                .feeConvertedAmount(fee.convertedAmount())
                .createdAt(domain.createdAt())
                .build();
    }

    public static TransactionMatch toDomain(RunnerTransactionMatchEntity entity) {
        Fee fee = new Fee(
                entity.getFeeAmount(),
                entity.getFeeAsset(),
                entity.getFeeType() != null ? entity.getFeeType() : FeeType.UNKNOWN,
                entity.getFeeConvertedAmount()
        );
        return new TransactionMatch(
                entity.getId(),
                entity.getRunnerId(),
                entity.getBuyTransactionId(),
                entity.getSellTransactionId(),
                entity.getMatchedQuantity(),
                entity.getBuyPrice(),
                entity.getSellPrice(),
                fee,
                entity.getPnlRealized(),
                entity.getCreatedAt()
        );
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static Set<RunnerMarketDataSourceEntity> toSourceEntities(Set<String> sources) {
        if (sources == null) return Collections.emptySet();
        return sources.stream()
                .map(s -> new RunnerMarketDataSourceEntity(s.toUpperCase()))
                .collect(Collectors.toSet());
    }

    private static Set<String> fromSourceEntities(Set<RunnerMarketDataSourceEntity> entities) {
        if (entities == null) return Collections.emptySet();
        return entities.stream()
                .map(RunnerMarketDataSourceEntity::source)
                .collect(Collectors.toSet());
    }
}
