package com.marmitt.application.spring.infrastructure.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionMatchEntity {

    private UUID buyTransactionId;
    private UUID sellTransactionId;
    private BigDecimal matchedQuantity;
    private Instant createdAt;
}
