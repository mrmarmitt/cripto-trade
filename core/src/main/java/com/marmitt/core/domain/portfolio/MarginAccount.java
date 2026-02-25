package com.marmitt.core.domain.portfolio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Controla o capital reservado por exchange/sub-conta de um Portfolio.
 * Permite que um Portfolio opere em múltiplas exchanges simultaneamente,
 * cada uma com sua própria contabilidade de margem.
 * <p>
 * Constraint: UNIQUE(portfolioId, exchangeId) — no máximo uma conta por exchange por portfolio.
 *
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 3.2.3, Blueprint 3, 7.A</a>
 */
public class MarginAccount {

    private final UUID id;
    private final UUID portfolioId;
    private final String exchangeId;
    private BigDecimal reservedCapital;
    private BigDecimal totalFeesPaid;
    private boolean isActive;
    private final Instant createdAt;
    private Instant updatedAt;
    private Long version;

    public MarginAccount(UUID portfolioId, String exchangeId) {
        this.id = UUID.randomUUID();
        this.portfolioId = Objects.requireNonNull(portfolioId, "portfolioId cannot be null");
        this.exchangeId = validateExchangeId(exchangeId);
        this.reservedCapital = BigDecimal.ZERO;
        this.totalFeesPaid = BigDecimal.ZERO;
        this.isActive = true;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.version = null;
    }

    /**
     * Construtor completo para reconstituição a partir do banco de dados.
     */
    public MarginAccount(
            UUID id,
            UUID portfolioId,
            String exchangeId,
            BigDecimal reservedCapital,
            BigDecimal totalFeesPaid,
            boolean isActive,
            Instant createdAt,
            Instant updatedAt,
            Long version
    ) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.portfolioId = Objects.requireNonNull(portfolioId, "portfolioId cannot be null");
        this.exchangeId = validateExchangeId(exchangeId);
        this.reservedCapital = Objects.requireNonNull(reservedCapital, "reservedCapital cannot be null");
        this.totalFeesPaid = Objects.requireNonNull(totalFeesPaid, "totalFeesPaid cannot be null");
        this.isActive = isActive;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt cannot be null");
        this.version = version;
    }

    /**
     * Adiciona capital reservado para ordens desta exchange.
     */
    public void addReservedCapital(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount cannot be null");
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Reserved capital amount must be positive");
        }
        this.reservedCapital = this.reservedCapital.add(amount);
        this.updatedAt = Instant.now();
    }

    /**
     * Libera capital reservado após execução ou cancelamento.
     */
    public void releaseReservedCapital(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount cannot be null");
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Release amount must be positive");
        }
        this.reservedCapital = this.reservedCapital.subtract(amount).max(BigDecimal.ZERO);
        this.updatedAt = Instant.now();
    }

    /**
     * Acumula fee paga nesta exchange (em baseCurrency).
     */
    public void accumulateFee(BigDecimal feeAmount) {
        Objects.requireNonNull(feeAmount, "feeAmount cannot be null");
        if (feeAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Fee amount cannot be negative");
        }
        this.totalFeesPaid = this.totalFeesPaid.add(feeAmount);
        this.updatedAt = Instant.now();
    }

    public void deactivate() {
        this.isActive = false;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getPortfolioId() { return portfolioId; }
    public String getExchangeId() { return exchangeId; }
    public BigDecimal getReservedCapital() { return reservedCapital; }
    public BigDecimal getTotalFeesPaid() { return totalFeesPaid; }
    public boolean isActive() { return isActive; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }

    private static String validateExchangeId(String exchangeId) {
        Objects.requireNonNull(exchangeId, "exchangeId cannot be null");
        if (exchangeId.isBlank()) {
            throw new IllegalArgumentException("exchangeId cannot be blank");
        }
        return exchangeId.toUpperCase();
    }
}
