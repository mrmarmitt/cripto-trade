package com.marmitt.core.domain.runner;

import com.marmitt.core.enums.PositionStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Posição de mercado gerenciada pelo StrategyRunner.
 * Deixa de ser 1:1 com Portfolio e passa a ser 1:N com StrategyRunner,
 * permitindo múltiplas posições simultâneas (Hedging).
 * <p>
 * <b>Convenção de moeda:</b> os campos numéricos seguem o padrão de pares da exchange
 * (base asset + quote asset concatenados, ex: "BTCUSDT"):
 * <ul>
 *   <li>{@code quantity} — em base asset (ex: BTC para BTCUSDT)</li>
 *   <li>{@code averagePrice}, {@code currentPrice} — em quote asset (ex: USDT para BTCUSDT)</li>
 *   <li>{@code realizedPnl} — em quote asset</li>
 * </ul>
 * A separação entre base e quote asset é responsabilidade do caller — tipicamente inferida
 * pelo {@code StrategyRunner} a partir da configuração do par ({@code baseCurrency} / {@code quoteCurrency})
 * registrada no próprio Runner. Não há lógica de parsing de symbol dentro desta classe.
 * <p>
 * O campo {@code lockedByTransactionId} implementa o locking provisório de lotes:
 * impede que uma posição em processo de venda seja usada por outro sinal concorrente.
 *
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 3.3.2, Blueprint 7.B, 9.3</a>
 */
public class Position {

    private final UUID id;
    private final UUID runnerId;
    private final String symbol;

    private PositionStatus status;

    /**
     * Quantidade total detida. Moeda = base asset do symbol (ex: BTC para BTCUSDT).
     */
    private BigDecimal quantity;

    /**
     * Preço médio ponderado (WAP). Campo persistido — não recalculado on-the-fly.
     *
     * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Blueprint 9.3.C</a>
     */
    private BigDecimal averagePrice;

    /**
     * Último preço de mercado. Atualizado a cada tick. Nullable.
     */
    private BigDecimal currentPrice;

    /**
     * PnL realizado acumulado desta posição (líquido de fees).
     */
    private BigDecimal realizedPnl;

    private final Instant openedAt;
    private Instant closedAt;
    private Instant updatedAt;

    // ── Locking de lotes (IG 9.3) ──────────────────────────────────────────
    /** FK para a transação de venda que reivindica este lote. NULL = disponível. */
    private UUID lockedByTransactionId;
    /** Quantidade reservada para a venda em curso. ≤ quantity disponível. */
    private BigDecimal lockedQuantity;
    /** Timestamp do lock — para observabilidade e debug. */
    private Instant lockedAt;
    // ───────────────────────────────────────────────────────────────────────

    private Long version;

    /**
     * Construtor para abertura de nova posição.
     */
    public Position(UUID runnerId, String symbol, BigDecimal quantity, BigDecimal averagePrice) {
        this.id = UUID.randomUUID();
        this.runnerId = Objects.requireNonNull(runnerId, "runnerId cannot be null");
        this.symbol = Objects.requireNonNull(symbol, "symbol cannot be null");
        this.quantity = requirePositive(quantity, "quantity");
        this.averagePrice = requirePositive(averagePrice, "averagePrice");
        this.currentPrice = averagePrice;
        this.status = PositionStatus.OPEN;
        this.realizedPnl = BigDecimal.ZERO;
        this.openedAt = Instant.now();
        this.updatedAt = Instant.now();
        this.version = 0L;
    }

    /**
     * Construtor completo para reconstituição a partir do banco de dados.
     * Use {@code Position.reconstitute().id(...).runnerId(...) ... .build()} via o Builder gerado.
     */
    @Builder(builderMethodName = "reconstitute")
    public Position(
            UUID id,
            UUID runnerId,
            String symbol,
            PositionStatus status,
            BigDecimal quantity,
            BigDecimal averagePrice,
            BigDecimal currentPrice,
            BigDecimal realizedPnl,
            Instant openedAt,
            Instant closedAt,
            UUID lockedByTransactionId,
            BigDecimal lockedQuantity,
            Instant lockedAt,
            Instant updatedAt,
            Long version
    ) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.runnerId = Objects.requireNonNull(runnerId, "runnerId cannot be null");
        this.symbol = Objects.requireNonNull(symbol, "symbol cannot be null");
        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.quantity = Objects.requireNonNull(quantity, "quantity cannot be null");
        this.averagePrice = Objects.requireNonNull(averagePrice, "averagePrice cannot be null");
        this.currentPrice = currentPrice;
        this.realizedPnl = Objects.requireNonNull(realizedPnl, "realizedPnl cannot be null");
        this.openedAt = Objects.requireNonNull(openedAt, "openedAt cannot be null");
        this.closedAt = closedAt;
        this.lockedByTransactionId = lockedByTransactionId;
        this.lockedQuantity = lockedQuantity;
        this.lockedAt = lockedAt;
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt cannot be null");
        this.version = version;
    }

    // ============================================================
    // Position management
    // ============================================================

    /**
     * Aumenta a posição com nova compra, recalculando o preço médio ponderado (WAP).
     *
     * @param additionalQuantity quantidade comprada
     * @param purchasePrice      preço de execução da compra
     */
    public void addQuantity(BigDecimal additionalQuantity, BigDecimal purchasePrice) {
        requirePositive(additionalQuantity, "additionalQuantity");
        requirePositive(purchasePrice, "purchasePrice");

        BigDecimal currentValue = this.quantity.multiply(this.averagePrice);
        BigDecimal additionalValue = additionalQuantity.multiply(purchasePrice);
        BigDecimal newQuantity = this.quantity.add(additionalQuantity);

        this.averagePrice = currentValue.add(additionalValue)
                .divide(newQuantity, 8, RoundingMode.HALF_UP);
        this.quantity = newQuantity;
        this.updatedAt = Instant.now();
    }

    /**
     * Reduz a posição após execução de venda e acumula PnL realizado.
     * <p>
     * {@code feePaid} deve estar <b>já convertida para a quote currency do símbolo</b>
     * (ex: USDT para BTCUSDT) antes de ser passada. Se a exchange cobrou a fee em outro
     * ativo (ex: BNB), a conversão é responsabilidade do caller — tipicamente o StrategyRunner
     * ao processar o callback da exchange via {@code Fee.convertedAmount()}.
     * Em caso de falha na conversão, o débito deve ser registrado na {@code DustAccount}
     * como {@code TECHNICAL_DEBT} e {@code feePaid = ZERO} usado aqui.
     *
     * @param soldQuantity quantidade vendida (em base asset, ex: BTC)
     * @param salePrice    preço de execução da venda (em quote asset, ex: USDT)
     * @param feePaid      fee já convertida para quote asset (ex: USDT); nunca negativo
     */
    public void reduceQuantity(BigDecimal soldQuantity, BigDecimal salePrice, BigDecimal feePaid) {
        requirePositive(soldQuantity, "soldQuantity");
        requirePositive(salePrice, "salePrice");
        Objects.requireNonNull(feePaid, "feePaid cannot be null");
        if (feePaid.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("feePaid cannot be negative");
        }

        if (soldQuantity.compareTo(this.quantity) > 0) {
            throw new IllegalArgumentException(
                    "Cannot sell more than current quantity: " + soldQuantity + " > " + this.quantity);
        }

        BigDecimal costBasis = soldQuantity.multiply(this.averagePrice);
        BigDecimal saleValue = soldQuantity.multiply(salePrice);
        BigDecimal pnl = saleValue.subtract(costBasis).subtract(feePaid);

        this.quantity = this.quantity.subtract(soldQuantity);
        this.realizedPnl = this.realizedPnl.add(pnl);
        this.updatedAt = Instant.now();

        if (this.quantity.compareTo(BigDecimal.ZERO) == 0) {
            close();
        }
    }

    /**
     * Atualiza o preço de mercado atual (chamado a cada tick de preço).
     */
    public void updateCurrentPrice(BigDecimal price) {
        requirePositive(price, "price");
        this.currentPrice = price;
        this.updatedAt = Instant.now();
    }

    // ============================================================
    // Lifecycle
    // ============================================================

    /**
     * Marca a posição como CLOSING — há ordens de venda em voo.
     */
    public void startClosing() {
        if (this.status != PositionStatus.OPEN) {
            throw new IllegalStateException("Cannot start closing from status: " + this.status);
        }
        this.status = PositionStatus.CLOSING;
        this.updatedAt = Instant.now();
    }

    /**
     * Reabre posição para OPEN (ex: venda cancelada).
     */
    public void reopen() {
        if (this.status != PositionStatus.CLOSING) {
            throw new IllegalStateException("Cannot reopen from status: " + this.status);
        }
        this.status = PositionStatus.OPEN;
        this.updatedAt = Instant.now();
    }

    private void close() {
        this.status = PositionStatus.CLOSED;
        this.closedAt = Instant.now();
    }

    // ============================================================
    // Locking (IG 9.3)
    // ============================================================

    /**
     * Aplica lock provisório para uma transação de venda.
     * Impede que outro sinal concorrente use esta posição.
     *
     * @param transactionId ID da transação de venda que reivindica o lote
     * @param quantity      quantidade reservada para a venda
     */
    public void lock(UUID transactionId, BigDecimal quantity) {
        if (this.lockedByTransactionId != null) {
            throw new IllegalStateException(
                    "Position already locked by transaction: " + this.lockedByTransactionId);
        }
        Objects.requireNonNull(transactionId, "transactionId cannot be null");
        requirePositive(quantity, "lock quantity");

        this.lockedByTransactionId = transactionId;
        this.lockedQuantity = quantity;
        this.lockedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * Remove o lock provisório (ex: venda cancelada ou expirada).
     */
    public void unlock() {
        this.lockedByTransactionId = null;
        this.lockedQuantity = null;
        this.lockedAt = null;
        this.updatedAt = Instant.now();
    }

    public boolean isLocked() {
        return this.lockedByTransactionId != null;
    }

    // ============================================================
    // Computed values
    // ============================================================

    /**
     * PnL não realizado calculado com o preço de mercado atual.
     * Retorna ZERO se currentPrice não disponível.
     */
    public BigDecimal getUnrealizedPnl() {
        if (currentPrice == null || quantity.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return currentPrice.subtract(averagePrice).multiply(quantity);
    }

    public boolean isClosed() {
        return status == PositionStatus.CLOSED;
    }

    public boolean isOpen() {
        return status == PositionStatus.OPEN;
    }

    // ============================================================
    // Getters
    // ============================================================

    public UUID getId() { return id; }
    public UUID getRunnerId() { return runnerId; }
    public String getSymbol() { return symbol; }
    public PositionStatus getStatus() { return status; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getAveragePrice() { return averagePrice; }
    public BigDecimal getCurrentPrice() { return currentPrice; }
    public BigDecimal getRealizedPnl() { return realizedPnl; }
    public Instant getOpenedAt() { return openedAt; }
    public Instant getClosedAt() { return closedAt; }
    public UUID getLockedByTransactionId() { return lockedByTransactionId; }
    public BigDecimal getLockedQuantity() { return lockedQuantity; }
    public Instant getLockedAt() { return lockedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }

    // ============================================================
    // Helpers
    // ============================================================

    private static BigDecimal requirePositive(BigDecimal value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " cannot be null");
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return value;
    }
}
