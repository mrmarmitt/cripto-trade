package com.marmitt.core.domain.runner;

import com.marmitt.core.enums.AccountingPolicyType;
import com.marmitt.core.enums.ExecutionPolicy;
import com.marmitt.core.enums.RunnerStatus;
import com.marmitt.core.exceptions.RunnerOperationException;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Aggregate Root do StrategyRunner — mestre operacional de uma estratégia específica.
 * Gerencia o ciclo de vida das ordens, contabilidade de posições e matching de transações.
 * Cada Runner opera um único símbolo em uma única exchange.
 * <p>
 * Campos operacionais migrados do Portfolio legado:
 * {@code strategyId}, {@code strategyName}, {@code symbol}, {@code exchangeId},
 * {@code allowedMarketDataSources}.
 *
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 3.3.1, Blueprint 2.B, 7.B, 12, 13</a>
 */
@Getter
public class StrategyRunner {

    private final UUID id;
    private final UUID portfolioId;

    /**
     * Código curto único (2-4 chars) usado no prefixo do clientOrderId para roteamento.
     * Ex: "01f", "a2b". Imutável após criação.
     */
    private final String shortCode;

    // Configuração de estratégia (migrado de Portfolio)
    private final UUID strategyId;
    private final String strategyName;

    // Configuração operacional (migrado de Portfolio)
    private final String symbol;
    private final String exchangeId;
    private final Set<String> allowedMarketDataSources;

    // Lifecycle
    private RunnerStatus status;

    // Políticas
    private final ExecutionPolicy executionPolicy;
    private final AccountingPolicyType accountingPolicyType;

    // Limites de capital
    private BigDecimal maxAllocationPercent;
    private int maxOpenPositions;
    private int maxPendingOrders;

    /** Proteção do lote independente da estratégia (T44). */
    private PositionProtection positionProtection;

    /**
     * Fatia fixa reservada para este Runner em modo DEDICATED.
     * Null em modo SHARED.
     */
    private final BigDecimal dedicatedBudget;

    /**
     * Flag de Boot Sequence — rejeita sinais enquanto true.
     *
     * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Blueprint 6.D</a>
     */
    private boolean isReconciling;

    private final Instant createdAt;
    private Instant statusChangedAt;
    private Instant lastReconciliationAt;

    /**
     * Soft delete — nunca remover fisicamente.
     *
     * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Blueprint 13.C, Nota #27</a>
     */
    private Instant archivedAt;

    private Long version;

    /**
     * Construtor principal para criação de um novo Runner.
     */
    public StrategyRunner(
            UUID id,
            UUID portfolioId,
            String shortCode,
            UUID strategyId,
            String strategyName,
            String symbol,
            String exchangeId,
            Set<String> allowedMarketDataSources,
            ExecutionPolicy executionPolicy,
            AccountingPolicyType accountingPolicyType,
            BigDecimal maxAllocationPercent,
            int maxOpenPositions,
            int maxPendingOrders,
            BigDecimal dedicatedBudget
    ) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.portfolioId = Objects.requireNonNull(portfolioId, "portfolioId cannot be null");
        this.shortCode = validateShortCode(shortCode);
        this.strategyId = Objects.requireNonNull(strategyId, "strategyId cannot be null");
        this.strategyName = Objects.requireNonNull(strategyName, "strategyName cannot be null");
        this.symbol = Objects.requireNonNull(symbol, "symbol cannot be null");
        this.exchangeId = Objects.requireNonNull(exchangeId, "exchangeId cannot be null");
        this.allowedMarketDataSources = allowedMarketDataSources != null
                ? Collections.unmodifiableSet(allowedMarketDataSources)
                : Collections.emptySet();
        this.executionPolicy = Objects.requireNonNull(executionPolicy, "executionPolicy cannot be null");
        this.accountingPolicyType = Objects.requireNonNull(accountingPolicyType, "accountingPolicyType cannot be null");
        this.maxAllocationPercent = Objects.requireNonNull(maxAllocationPercent, "maxAllocationPercent cannot be null");
        this.maxOpenPositions = maxOpenPositions > 0 ? maxOpenPositions : 1;
        this.maxPendingOrders = maxPendingOrders > 0 ? maxPendingOrders : 1;
        this.dedicatedBudget = dedicatedBudget;
        this.positionProtection = PositionProtection.defaults();
        this.status = RunnerStatus.CREATED;
        this.isReconciling = false;
        this.createdAt = Instant.now();
        this.statusChangedAt = this.createdAt;
        this.lastReconciliationAt = null;
        this.archivedAt = null;
        this.version = null;
    }

    /**
     * Construtor completo para reconstituição a partir do banco de dados.
     */
    public StrategyRunner(
            UUID id,
            UUID portfolioId,
            String shortCode,
            UUID strategyId,
            String strategyName,
            String symbol,
            String exchangeId,
            Set<String> allowedMarketDataSources,
            RunnerStatus status,
            ExecutionPolicy executionPolicy,
            AccountingPolicyType accountingPolicyType,
            BigDecimal maxAllocationPercent,
            int maxOpenPositions,
            int maxPendingOrders,
            BigDecimal dedicatedBudget,
            PositionProtection positionProtection,
            boolean isReconciling,
            Instant createdAt,
            Instant statusChangedAt,
            Instant lastReconciliationAt,
            Instant archivedAt,
            Long version
    ) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.portfolioId = Objects.requireNonNull(portfolioId, "portfolioId cannot be null");
        this.shortCode = validateShortCode(shortCode);
        this.strategyId = Objects.requireNonNull(strategyId, "strategyId cannot be null");
        this.strategyName = Objects.requireNonNull(strategyName, "strategyName cannot be null");
        this.symbol = Objects.requireNonNull(symbol, "symbol cannot be null");
        this.exchangeId = Objects.requireNonNull(exchangeId, "exchangeId cannot be null");
        this.allowedMarketDataSources = allowedMarketDataSources != null
                ? Collections.unmodifiableSet(allowedMarketDataSources)
                : Collections.emptySet();
        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.executionPolicy = Objects.requireNonNull(executionPolicy, "executionPolicy cannot be null");
        this.accountingPolicyType = Objects.requireNonNull(accountingPolicyType, "accountingPolicyType cannot be null");
        this.maxAllocationPercent = Objects.requireNonNull(maxAllocationPercent, "maxAllocationPercent cannot be null");
        this.maxOpenPositions = maxOpenPositions;
        this.maxPendingOrders = maxPendingOrders;
        this.dedicatedBudget = dedicatedBudget;
        this.positionProtection = Objects.requireNonNull(positionProtection, "positionProtection cannot be null");
        this.isReconciling = isReconciling;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null");
        this.statusChangedAt = statusChangedAt != null ? statusChangedAt : createdAt;
        this.lastReconciliationAt = lastReconciliationAt;
        this.archivedAt = archivedAt;
        this.version = version;
    }

    // ============================================================
    // Lifecycle transitions
    // ============================================================

    /**
     * Inicia o processo de inicialização (Boot Sequence).
     * CREATED → INITIALIZING
     */
    public void startInitializing() {
        if (this.status == RunnerStatus.INITIALIZING) {
            // Start anterior parou na reconciliacao: o novo start retoma dali (T49).
            return;
        }
        requireStatus(RunnerStatus.CREATED);
        this.status = RunnerStatus.INITIALIZING;
        this.statusChangedAt = Instant.now();
        this.isReconciling = true;
    }

    /**
     * Marca o Runner como ativo após Boot Sequence bem-sucedida.
     * INITIALIZING → ACTIVE
     */
    public void activate() {
        requireStatus(RunnerStatus.INITIALIZING);
        this.status = RunnerStatus.ACTIVE;
        this.statusChangedAt = Instant.now();
        this.isReconciling = false;
        this.lastReconciliationAt = Instant.now();
    }

    /**
     * Pausa o Runner (kill switch ou erro de reconciliação).
     * ACTIVE ou TERMINATING → HALTED. Um runner em encerramento também para por completo; ao
     * retomar ele volta a ACTIVE e o encerramento precisa ser pedido de novo (T49).
     */
    public void halt() {
        if (this.status != RunnerStatus.ACTIVE && this.status != RunnerStatus.TERMINATING) {
            throw new RunnerOperationException("Cannot halt runner in status " + this.status);
        }
        this.status = RunnerStatus.HALTED;
        this.statusChangedAt = Instant.now();
    }

    /**
     * Retoma operação após HALTED.
     * HALTED → ACTIVE
     */
    public void resume() {
        requireStatus(RunnerStatus.HALTED);
        this.status = RunnerStatus.ACTIVE;
        this.statusChangedAt = Instant.now();
    }

    /**
     * Inicia processo de encerramento (aguarda ordens em voo).
     * ACTIVE ou HALTED → TERMINATING
     */
    public void startTerminating() {
        if (this.status != RunnerStatus.ACTIVE && this.status != RunnerStatus.HALTED) {
            throw new RunnerOperationException("Cannot start terminating from status: " + this.status);
        }
        this.status = RunnerStatus.TERMINATING;
        this.statusChangedAt = Instant.now();
    }

    /**
     * Arquiva o Runner após encerramento completo (soft delete).
     * TERMINATING → ARCHIVED
     */
    public void archive() {
        requireStatus(RunnerStatus.TERMINATING);
        this.status = RunnerStatus.ARCHIVED;
        this.statusChangedAt = Instant.now();
        this.archivedAt = Instant.now();
    }

    // ============================================================
    // Boot Sequence / Reconciliation
    // ============================================================

    /**
     * Registra conclusão de reconciliação bem-sucedida.
     */
    public void completeReconciliation() {
        this.isReconciling = false;
        this.lastReconciliationAt = Instant.now();
    }

    /**
     * Marks reconciliation start to block signal processing during boot/recovery.
     */
    public void beginReconciliation() {
        if (this.status == RunnerStatus.ARCHIVED || this.status == RunnerStatus.TERMINATING) {
            throw new RunnerOperationException("Cannot start reconciliation for status: " + this.status);
        }
        this.isReconciling = true;
    }

    // ============================================================
    // Query methods
    // ============================================================

    /**
     * Runner conduzido pelo pipeline de sinal (estratégia ou Safe Mode): ACTIVE ou TERMINATING,
     * fora de reconciliação. Em TERMINATING ele ainda vende para fechar posição (T49).
     */
    public boolean canAcceptSignals() {
        return (status == RunnerStatus.ACTIVE || status == RunnerStatus.TERMINATING) && !isReconciling;
    }

    /**
     * Runner pode abrir posição (BUY): só ACTIVE, fora de reconciliação.
     */
    public boolean canOpenPositions() {
        return status == RunnerStatus.ACTIVE && !isReconciling;
    }

    /**
     * Altera os limites mutáveis em runtime (IG §13.2.2, T49) e a proteção do lote (T44). {@code null}
     * mantém o valor atual; vale a partir da próxima reserva ou do próximo tick. {@code maxHoldingDuration}
     * zero desliga a idade máxima. Políticas, estratégia e símbolo são imutáveis.
     */
    public void updateLimits(BigDecimal maxAllocationPercent, Integer maxOpenPositions, Integer maxPendingOrders,
                             BigDecimal maxLossPercentPerLot, Duration maxHoldingDuration) {
        if (this.status == RunnerStatus.ARCHIVED) {
            throw new RunnerOperationException("Cannot change limits of an archived runner: " + id);
        }
        if (maxAllocationPercent != null
                && (maxAllocationPercent.signum() <= 0 || maxAllocationPercent.compareTo(BigDecimal.ONE) > 0)) {
            throw new IllegalArgumentException("maxAllocationPercent must be in (0, 1]: " + maxAllocationPercent);
        }
        if (maxOpenPositions != null) {
            requirePositiveLimit(maxOpenPositions, "maxOpenPositions");
        }
        if (maxPendingOrders != null) {
            requirePositiveLimit(maxPendingOrders, "maxPendingOrders");
        }
        // Valida tudo antes de alterar: pedido com um campo inválido não aplica os demais.
        PositionProtection protection = new PositionProtection(
                maxLossPercentPerLot != null ? maxLossPercentPerLot : positionProtection.maxLossPercentPerLot(),
                maxHoldingDuration == null ? positionProtection.maxHoldingDuration()
                        : maxHoldingDuration.isZero() ? null : maxHoldingDuration);

        if (maxAllocationPercent != null) {
            this.maxAllocationPercent = maxAllocationPercent;
        }
        if (maxOpenPositions != null) {
            this.maxOpenPositions = maxOpenPositions;
        }
        if (maxPendingOrders != null) {
            this.maxPendingOrders = maxPendingOrders;
        }
        this.positionProtection = protection;
    }

    /**
     * Verifica se o Runner pode receber market data de uma exchange específica.
     */
    public boolean canReceiveMarketDataFrom(String exchange) {
        return allowedMarketDataSources.isEmpty()
                || allowedMarketDataSources.contains(exchange.toUpperCase());
    }

    /**
     * Verifica se o Runner opera no símbolo e exchange informados.
     */
    public boolean operates(String symbol, String exchangeId) {
        return this.symbol.equalsIgnoreCase(symbol)
                && this.exchangeId.equalsIgnoreCase(exchangeId);
    }

    // ============================================================
    // Private helpers
    // ============================================================

    private static String validateShortCode(String shortCode) {
        Objects.requireNonNull(shortCode, "shortCode cannot be null");
        if (shortCode.length() < 2 || shortCode.length() > 4) {
            throw new IllegalArgumentException(
                    "shortCode must be 2-4 characters, got: " + shortCode);
        }
        return shortCode.toLowerCase();
    }

    private void requireStatus(RunnerStatus expected) {
        if (this.status != expected) {
            throw new RunnerOperationException("Expected status " + expected + " but was " + this.status);
        }
    }

    private static int requirePositiveLimit(int value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + " must be positive: " + value);
        }
        return value;
    }
}
