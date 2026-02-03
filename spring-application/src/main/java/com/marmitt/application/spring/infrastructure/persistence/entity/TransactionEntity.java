package com.marmitt.application.spring.infrastructure.persistence.entity;

import com.marmitt.core.enums.AssetType;
import com.marmitt.core.enums.TransactionStatus;
import com.marmitt.core.enums.TransactionType;
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

@Table("transactions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionEntity {

    @Id
    private UUID id;

    private UUID portfolioId;
    private String clientOrderId;
    private TransactionStatus status;
    private TransactionType type;
    private String symbol;

    // Quantity
    private BigDecimal quantityAmount;
    private String quantityCurrency;
    private AssetType quantityAssetType;

    // Executed Quantity (nullable)
    private BigDecimal executedQuantityAmount;
    private String executedQuantityCurrency;
    private AssetType executedQuantityAssetType;

    // Price
    private BigDecimal priceAmount;
    private String priceCurrency;
    private AssetType priceAssetType;

    // Executed Price (nullable)
    private BigDecimal executedPriceAmount;
    private String executedPriceCurrency;
    private AssetType executedPriceAssetType;

    // Total
    private BigDecimal totalAmount;
    private String totalCurrency;
    private AssetType totalAssetType;

    // Fee
    private BigDecimal feeAmount;
    private String feeCurrency;
    private AssetType feeAssetType;

    // Timestamps
    private Instant requestedAt;
    private Instant executedAt;

    // Rejection
    private String rejectReason;

    @Version
    private Long version;
}
