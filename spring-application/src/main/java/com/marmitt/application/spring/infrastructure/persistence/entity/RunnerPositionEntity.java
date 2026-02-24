package com.marmitt.application.spring.infrastructure.persistence.entity;

import com.marmitt.core.enums.PositionStatus;
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

@Table("positions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RunnerPositionEntity {

    @Id
    private UUID id;

    private UUID runnerId;
    private String symbol;
    private PositionStatus status;

    private BigDecimal quantity;
    private BigDecimal averagePrice;
    private BigDecimal currentPrice;
    private BigDecimal realizedPnl;

    private Instant openedAt;
    private Instant closedAt;
    private Instant updatedAt;

    // Locking fields (IG 9.3)
    private UUID lockedByTransactionId;
    private BigDecimal lockedQuantity;
    private Instant lockedAt;

    @Version
    private Long version;
}
