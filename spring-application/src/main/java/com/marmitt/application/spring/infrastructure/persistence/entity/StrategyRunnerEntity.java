package com.marmitt.application.spring.infrastructure.persistence.entity;

import com.marmitt.core.enums.AccountingPolicyType;
import com.marmitt.core.enums.ExecutionPolicy;
import com.marmitt.core.enums.RunnerStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Entidade de persistência para StrategyRunner.
 * <p>
 * {@code allowedMarketDataSources} é persistido via {@code @MappedCollection} na tabela
 * {@code runner_market_data_sources} (FK runner_id). Spring Data JDBC carrega a coleção
 * automaticamente ao buscar o aggregate root.
 */
@Table("strategy_runners")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StrategyRunnerEntity {

    @Id
    private UUID id;

    private UUID portfolioId;

    /** Código curto de roteamento (2-4 chars). */
    private String shortCode;

    private UUID strategyId;
    private String strategyName;

    private String symbol;
    private String exchangeId;

    @MappedCollection(idColumn = "runner_id")
    @Builder.Default
    private Set<RunnerMarketDataSourceEntity> allowedMarketDataSources = new HashSet<>();

    private RunnerStatus status;
    private ExecutionPolicy executionPolicy;
    private AccountingPolicyType accountingPolicyType;

    private BigDecimal maxAllocationPercent;
    private int maxOpenPositions;
    private int maxPendingOrders;

    /** Null em modo SHARED. */
    private BigDecimal dedicatedBudget;

    private boolean isReconciling;

    private Instant createdAt;
    private Instant statusChangedAt;
    private Instant lastReconciliationAt;
    private Instant archivedAt;

    @Version
    private Long version;
}
