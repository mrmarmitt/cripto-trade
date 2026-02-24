package com.marmitt.application.spring.infrastructure.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Entidade de persistência para GlobalBalance.
 * Tabela separada de {@code portfolio_balances} (modelo legado).
 * <p>
 * PK = {@code portfolio_id} — relação 1:1 com Portfolio.
 */
@Table("global_balances")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GlobalBalanceEntity {

    @Id
    private UUID portfolioId;

    private BigDecimal availableBalance;
    private BigDecimal reservedBalance;
    private BigDecimal realizedBalance;
    private BigDecimal initialCapital;
    private String baseCurrency;
    private BigDecimal totalFeesPaid;

    private Instant lastExecutionTime;
    private Instant updatedAt;

    @Version
    private Long version;
}
