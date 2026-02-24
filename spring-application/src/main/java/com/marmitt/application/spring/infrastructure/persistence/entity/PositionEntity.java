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

@Table("positions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PositionEntity {

    @Id
    @Column("portfolio_id")
    private UUID portfolioId;

    private String symbol;
    private BigDecimal quantityAmount;
    private String quantityCurrency;
    private BigDecimal averagePriceAmount;
    private String averagePriceCurrency;
    private BigDecimal currentPriceAmount;
    private String currentPriceCurrency;
    private Instant openedAt;
    private Instant updatedAt;

    @Version
    private Long version;
}
