package com.marmitt.core.domain.portfolio;

import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Opera em moeda única ({@code baseCurrency}), eliminando a redundância de currency/type por campo.
 * <p>
 * Semântica dos campos:
 * <ul>
 *   <li>{@code availableBalance} — poder de compra real (Total - Reserved - DustDebits)</li>
 *   <li>{@code reservedBalance} — margem congelada para ordens em voo (cresce no Capital Request, decresce na confirmação)</li>
 *   <li>{@code realizedBalance} — PnL líquido acumulado de todas as operações fechadas</li>
 *   <li>{@code totalFeesPaid} — soma incremental de todas as fees (métrica de eficiência)</li>
 * </ul>
 *
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 3.2.2</a>
 */
@Getter
public class GlobalBalance {

    private final UUID portfolioId;
    private BigDecimal availableBalance;
    private BigDecimal reservedBalance;
    private BigDecimal realizedBalance;
    private final BigDecimal initialCapital;
    private final String baseCurrency;
    private BigDecimal totalFeesPaid;
    private Instant lastExecutionTime;
    private Instant updatedAt;
    private Long version;

    public GlobalBalance(
            UUID portfolioId,
            BigDecimal initialCapital,
            String baseCurrency
    ) {
        this.portfolioId = Objects.requireNonNull(portfolioId, "portfolioId cannot be null");
        this.initialCapital = Objects.requireNonNull(initialCapital, "initialCapital cannot be null");
        this.baseCurrency = Objects.requireNonNull(baseCurrency, "baseCurrency cannot be null");
        this.availableBalance = initialCapital;
        this.reservedBalance = BigDecimal.ZERO;
        this.realizedBalance = BigDecimal.ZERO;
        this.totalFeesPaid = BigDecimal.ZERO;
        this.lastExecutionTime = null;
        this.updatedAt = Instant.now();
        this.version = null;
    }

    /**
     * Construtor completo para reconstituição a partir do banco de dados.
     */
    public GlobalBalance(
            UUID portfolioId,
            BigDecimal availableBalance,
            BigDecimal reservedBalance,
            BigDecimal realizedBalance,
            BigDecimal initialCapital,
            String baseCurrency,
            BigDecimal totalFeesPaid,
            Instant lastExecutionTime,
            Instant updatedAt,
            Long version
    ) {
        this.portfolioId = Objects.requireNonNull(portfolioId, "portfolioId cannot be null");
        this.availableBalance = Objects.requireNonNull(availableBalance, "availableBalance cannot be null");
        this.reservedBalance = Objects.requireNonNull(reservedBalance, "reservedBalance cannot be null");
        this.realizedBalance = Objects.requireNonNull(realizedBalance, "realizedBalance cannot be null");
        this.initialCapital = Objects.requireNonNull(initialCapital, "initialCapital cannot be null");
        this.baseCurrency = Objects.requireNonNull(baseCurrency, "baseCurrency cannot be null");
        this.totalFeesPaid = Objects.requireNonNull(totalFeesPaid, "totalFeesPaid cannot be null");
        this.lastExecutionTime = lastExecutionTime;
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt cannot be null");
        this.version = version;
    }

    /**
     * Reserva capital para uma ordem (Capital Request — síncrono).
     * Move {@code amount} de Available → Reserved.
     *
     * @param amount valor a reservar (deve ser positivo e ≤ availableBalance)
     * @throws IllegalArgumentException se saldo insuficiente
     */
    public void reserve(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount cannot be null");
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Reserve amount must be positive");
        }
        if (availableBalance.compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient available balance for reservation");
        }
        this.availableBalance = this.availableBalance.subtract(amount);
        this.reservedBalance = this.reservedBalance.add(amount);
        this.updatedAt = Instant.now();
    }

    /**
     * Confirma execução — converte Reserved → Realized.
     * Chamado após TransactionMatch ser persistido.
     *
     * @param cost         custo total da operação (margem consumida)
     * @param pnlAmount    PnL líquido da operação
     * @param feeConverted fee convertida para baseCurrency
     */
    public void confirmExecution(BigDecimal cost, BigDecimal pnlAmount, BigDecimal feeConverted) {
        Objects.requireNonNull(cost, "cost cannot be null");
        Objects.requireNonNull(pnlAmount, "pnlAmount cannot be null");
        Objects.requireNonNull(feeConverted, "feeConverted cannot be null");
        if (cost.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Execution cost must be positive");
        }
        if (reservedBalance.compareTo(cost) < 0) {
            throw new IllegalStateException("Insufficient reserved balance for execution confirmation");
        }

        this.reservedBalance = this.reservedBalance.subtract(cost);
        this.realizedBalance = this.realizedBalance.add(pnlAmount);
        this.availableBalance = this.availableBalance.add(cost).add(pnlAmount);
        this.totalFeesPaid = this.totalFeesPaid.add(feeConverted);
        this.lastExecutionTime = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * Libera margem reservada — devolve Reserved → Available.
     * Chamado quando transação é REJECTED, CANCELED ou EXPIRED.
     *
     * @param amount valor a liberar
     */
    public void release(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount cannot be null");
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Release amount must be positive");
        }
        if (reservedBalance.compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient reserved balance for release");
        }
        this.reservedBalance = this.reservedBalance.subtract(amount);
        this.availableBalance = this.availableBalance.add(amount);
        this.updatedAt = Instant.now();
    }

    /**
     * Verifica se há saldo disponível suficiente para uma reserva.
     */
    public boolean hasAvailableBalance(BigDecimal amount) {
        return availableBalance.compareTo(amount) >= 0;
    }

    /**
     * Retorna o saldo total (available + reserved).
     */
    public BigDecimal getTotalBalance() {
        return availableBalance.add(reservedBalance);
    }
}
