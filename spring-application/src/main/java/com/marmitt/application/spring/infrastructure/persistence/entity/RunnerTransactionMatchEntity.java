package com.marmitt.application.spring.infrastructure.persistence.entity;

import com.marmitt.core.enums.FeeType;
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
 * Entidade imutável para runner.TransactionMatch.
 * Fee é desnormalizada inline para evitar JOINs em queries de P&L.
 * Sem @Version — nunca atualizado após criação.
 */
@Table("transaction_matches")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RunnerTransactionMatchEntity {

    @Id
    private UUID id;

    private UUID runnerId;
    private UUID buyTransactionId;
    private UUID sellTransactionId;

    private BigDecimal matchedQuantity;
    private BigDecimal buyPrice;
    private BigDecimal sellPrice;
    private BigDecimal pnlRealized;

    // Fee VO desnormalizado (evita tabela separada e JOIN)
    private BigDecimal feeAmount;
    private String feeAsset;
    private FeeType feeType;
    private BigDecimal feeConvertedAmount;

    private Instant createdAt;

    @Version
    private Long version;
}
