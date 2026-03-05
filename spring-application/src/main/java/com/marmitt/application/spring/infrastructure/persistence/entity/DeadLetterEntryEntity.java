package com.marmitt.application.spring.infrastructure.persistence.entity;

import com.marmitt.core.enums.DlqReason;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("dead_letter_entries")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeadLetterEntryEntity {

    @Id
    private UUID id;

    private UUID portfolioId;
    private UUID runnerId;
    private String clientOrderId;
    private String exchangeOrderId;
    private String rawPayload;
    private DlqReason reason;
    private boolean isResolved;
    private String resolvedBy;
    private Instant resolvedAt;
    private Instant createdAt;
}
