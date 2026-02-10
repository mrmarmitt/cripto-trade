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
import org.springframework.data.relational.core.mapping.Column;
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
    @Column("quantity_type")
    private AssetType quantityAssetType;

    // Executed Quantity (nullable)
    private BigDecimal executedQuantityAmount;
    private String executedQuantityCurrency;
    @Column("executed_quantity_type")
    private AssetType executedQuantityAssetType;

    // Price
    private BigDecimal priceAmount;
    private String priceCurrency;
    @Column("price_type")
    private AssetType priceAssetType;

    // Executed Price (nullable)
    private BigDecimal executedPriceAmount;
    private String executedPriceCurrency;
    @Column("executed_price_type")
    private AssetType executedPriceAssetType;

    // Total
    private BigDecimal totalAmount;
    private String totalCurrency;
    @Column("total_type")
    private AssetType totalAssetType;

    // Fee
    private BigDecimal feeAmount;
    private String feeCurrency;
    @Column("fee_type")
    private AssetType feeAssetType;

    // Timestamps
    private Instant requestedAt;
    private Instant executedAt;

    // Rejection
    private String rejectReason;

    // Lot targeting
    private UUID targetLotId;

    @Version
    private Long version;
}
