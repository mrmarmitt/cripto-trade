package com.marmitt.application.spring.infrastructure.persistence.entity;

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

@Table("portfolios")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioEntity {

    @Id
    private UUID id;

    private String name;
    private UUID strategyId;
    private String strategyName;
    private String symbol;
    private BigDecimal initialCapitalAmount;
    private String initialCapitalCurrency;
    private String orderExecutionExchange;
    private boolean isActive;
    private Instant createdAt;

    @MappedCollection(idColumn = "portfolio_id")
    @Builder.Default
    private Set<MarketDataSourceRef> allowedMarketDataSources = new HashSet<>();

    @Version
    private Long version;
}
