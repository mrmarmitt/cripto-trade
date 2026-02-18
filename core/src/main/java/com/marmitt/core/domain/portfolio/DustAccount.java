package com.marmitt.core.domain.portfolio;

import com.marmitt.core.enums.DustSourceType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Receptor universal de débitos não resolvidos e resíduos operacionais.
 * Seus valores são excluídos do {@code availableBalance} do GlobalBalance,
 * mantendo o capital de giro "limpo".
 * <p>
 * Fontes de entrada:
 * <ul>
 *   <li>{@code ROUNDING_DUST} — resíduo de arredondamento quando quantidade restante {@literal <} minQty da exchange</li>
 *   <li>{@code TECHNICAL_DEBT} — falha na conversão de fee cross-currency (Fallback Crítico)</li>
 * </ul>
 *
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 3.2.4, Blueprint 9.2.C</a>
 */
public class DustAccount {

    private final UUID id;
    private final UUID portfolioId;

    /**
     * Runner que gerou o resíduo. Nullable para dust do próprio Portfolio.
     */
    private final UUID runnerId;

    private final DustSourceType sourceType;
    private final String originalAsset;
    private final BigDecimal originalAmount;

    /**
     * Valor convertido para baseCurrency. Null = pendente de conversão.
     */
    private BigDecimal convertedAmount;

    /**
     * Transação que originou o resíduo. Nullable.
     */
    private final UUID transactionId;

    private boolean isResolved;
    private Instant resolvedAt;
    private final Instant createdAt;

    /**
     * Construtor para criação de novo registro de dust/debt.
     */
    public DustAccount(
            UUID portfolioId,
            UUID runnerId,
            DustSourceType sourceType,
            String originalAsset,
            BigDecimal originalAmount,
            UUID transactionId
    ) {
        this.id = UUID.randomUUID();
        this.portfolioId = Objects.requireNonNull(portfolioId, "portfolioId cannot be null");
        this.runnerId = runnerId;
        this.sourceType = Objects.requireNonNull(sourceType, "sourceType cannot be null");
        this.originalAsset = validateAsset(originalAsset);
        this.originalAmount = Objects.requireNonNull(originalAmount, "originalAmount cannot be null");
        this.transactionId = transactionId;
        this.convertedAmount = null;
        this.isResolved = false;
        this.resolvedAt = null;
        this.createdAt = Instant.now();
    }

    /**
     * Construtor completo para reconstituição a partir do banco de dados.
     */
    public DustAccount(
            UUID id,
            UUID portfolioId,
            UUID runnerId,
            DustSourceType sourceType,
            String originalAsset,
            BigDecimal originalAmount,
            BigDecimal convertedAmount,
            UUID transactionId,
            boolean isResolved,
            Instant resolvedAt,
            Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.portfolioId = Objects.requireNonNull(portfolioId, "portfolioId cannot be null");
        this.runnerId = runnerId;
        this.sourceType = Objects.requireNonNull(sourceType, "sourceType cannot be null");
        this.originalAsset = validateAsset(originalAsset);
        this.originalAmount = Objects.requireNonNull(originalAmount, "originalAmount cannot be null");
        this.convertedAmount = convertedAmount;
        this.transactionId = transactionId;
        this.isResolved = isResolved;
        this.resolvedAt = resolvedAt;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null");
    }

    /**
     * Registra o valor convertido para baseCurrency após precificação.
     */
    public void setConvertedAmount(BigDecimal convertedAmount) {
        Objects.requireNonNull(convertedAmount, "convertedAmount cannot be null");
        if (convertedAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("convertedAmount cannot be negative");
        }
        this.convertedAmount = convertedAmount;
    }

    /**
     * Marca o resíduo como resolvido (processado pelo Sweep Worker).
     */
    public void resolve() {
        if (this.isResolved) {
            throw new IllegalStateException("DustAccount already resolved: " + id);
        }
        this.isResolved = true;
        this.resolvedAt = Instant.now();
    }

    public boolean isPendingConversion() {
        return convertedAmount == null;
    }

    public UUID getId() { return id; }
    public UUID getPortfolioId() { return portfolioId; }
    public UUID getRunnerId() { return runnerId; }
    public DustSourceType getSourceType() { return sourceType; }
    public String getOriginalAsset() { return originalAsset; }
    public BigDecimal getOriginalAmount() { return originalAmount; }
    public BigDecimal getConvertedAmount() { return convertedAmount; }
    public UUID getTransactionId() { return transactionId; }
    public boolean isResolved() { return isResolved; }
    public Instant getResolvedAt() { return resolvedAt; }
    public Instant getCreatedAt() { return createdAt; }

    private static String validateAsset(String asset) {
        Objects.requireNonNull(asset, "originalAsset cannot be null");
        if (asset.isBlank()) {
            throw new IllegalArgumentException("originalAsset cannot be blank");
        }
        return asset.toUpperCase();
    }
}
