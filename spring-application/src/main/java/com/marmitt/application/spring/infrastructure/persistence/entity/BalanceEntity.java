package com.marmitt.application.spring.infrastructure.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Table("portfolio_balances")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BalanceEntity {

    @Id
    @Column("portfolio_id")
    private UUID portfolioId;

    private BigDecimal availableAmount;
    private String availableCurrency;
    private BigDecimal investedAmount;
    private String investedCurrency;

    @Column("realized_pnl")
    private BigDecimal realizedPnL;

    private Instant lastExecutionTime;
    private Instant updatedAt;

    @Version
    private Long version;
}
