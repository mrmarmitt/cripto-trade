package com.marmitt.core.domain.portfolio;

import com.marmitt.core.domain.Symbol;
import com.marmitt.core.domain.portfolio.contrats.AccountingPolicy;
import com.marmitt.core.dto.strategy.OpenBuyEntryDto;
import com.marmitt.core.dto.strategy.PendingSellEntryDto;
import com.marmitt.core.dto.strategy.PortfolioContextDto;
import com.marmitt.core.enums.CapitalPoolingMode;
import com.marmitt.core.enums.SafeModeStatus;
import com.marmitt.core.enums.TransactionStatus;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Getter
public class Portfolio {

    private final UUID id;
    private final String name;
    private boolean isActive;
    private final Instant createdAt;
    private Instant lastExecutionTime;

    // Novos campos — modelo alvo (IG 3.2.1)
    private SafeModeStatus safeModeStatus;
    private CapitalPoolingMode capitalPoolingMode;

    private final AccountingPolicy accountingPolicy;

    private final Balance balance;
    private final List<Transaction> transactions;
    private final List<TransactionMatch> transactionMatches = new ArrayList<>();

    // === Campos deprecados — migram para StrategyRunner (F1-04) ===

    /** @deprecated Migra para StrategyRunner.strategyId */
    @Deprecated(forRemoval = true)
    private final UUID strategyId;

    /** @deprecated Migra para StrategyRunner.strategyName */
    @Deprecated(forRemoval = true)
    private final String strategyName;

    /** @deprecated Migra para StrategyRunner.symbol */
    @Deprecated(forRemoval = true)
    private final Symbol symbol;

    /** @deprecated Migra para StrategyRunner.exchangeId */
    @Deprecated(forRemoval = true)
    private final String orderExecutionExchange;

    /** @deprecated Migra para runner_market_data_sources */
    @Deprecated(forRemoval = true)
    private final Set<String> allowedMarketDataSources;

    // Configurações de limitação
    private static final BigDecimal MINIMUM_OPERATION_AMOUNT = new BigDecimal("10.00");
    private static final Duration EXECUTION_COOLDOWN = Duration.ofSeconds(0);
    private static final BigDecimal DEFAULT_MAX_EXPOSURE_PERCENTAGE = new BigDecimal("0.20");

    public Portfolio(
            UUID id,
            String name,
            UUID strategyId,
            String strategyName,
            Symbol symbol, AccountingPolicy accountingPolicy,
            Asset initialCapital,
            String orderExecutionExchange,
            Set<String> allowedMarketDataSources
    ) {
        this.id = id;
        this.name = name;
        this.strategyId = strategyId;
        this.strategyName = strategyName;
        this.symbol = Objects.requireNonNull(symbol, "Symbol cannot be null");
        this.accountingPolicy = accountingPolicy;
        this.balance = Balance.withInitialCapital(initialCapital);
        this.transactions = new ArrayList<>();
        this.isActive = true;
        this.createdAt = Instant.now();
        this.lastExecutionTime = null;
        this.orderExecutionExchange = Objects.requireNonNull(orderExecutionExchange, "Order execution exchange cannot be null");
        this.allowedMarketDataSources = allowedMarketDataSources != null ?
                Collections.unmodifiableSet(allowedMarketDataSources) : null;
        this.safeModeStatus = SafeModeStatus.NORMAL;
        this.capitalPoolingMode = CapitalPoolingMode.SHARED;
    }

    // ============================================================
    // Safe Mode Management
    // ============================================================

    /**
     * Atualiza o nível do Safe Mode do Portfolio.
     *
     * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 3.2.1, Blueprint 11.1.B.1</a>
     */
    public void setSafeModeStatus(SafeModeStatus status) {
        this.safeModeStatus = Objects.requireNonNull(status, "SafeModeStatus cannot be null");
    }

    /**
     * Atualiza o modo de pooling de capital.
     *
     * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 3.2.1, Blueprint 11.2.C</a>
     */
    public void setCapitalPoolingMode(CapitalPoolingMode mode) {
        this.capitalPoolingMode = Objects.requireNonNull(mode, "CapitalPoolingMode cannot be null");
    }

    /**
     * Position como View calculada a partir do inventário de lotes abertos.
     * O preço de mercado é dado externo — sempre fornecido pelo caller.
     */
    public Position getPosition(Asset marketPrice) {
        return accountingPolicy.calculatePosition(this.symbol, this.transactions,
                this.transactionMatches, marketPrice);
    }

    /**
     * Verifica se existem lotes de compra abertos (independente de preço de mercado).
     */
    public boolean hasPosition() {
        return !getOpenBuyTransactions().isEmpty();
    }

    /**
     * Adiciona uma transaction PENDING ao portfolio.
     * Se for SELL, delega o matching para a AccountingPolicy e retorna os matches criados.
     * @return matches criados (lista vazia se BUY)
     */
    public List<TransactionMatch> addPendingTransaction(Transaction transaction) {
        Objects.requireNonNull(transaction, "Transaction cannot be null");
        if (transaction.status() != TransactionStatus.PENDING) {
            throw new IllegalArgumentException("Transaction must be PENDING status");
        }
        transactions.add(transaction);
        if (transaction.isSell()) {
            List<Transaction> availableBuys = getOpenBuyTransactions();
            List<TransactionMatch> newMatches = accountingPolicy.matchOrder(
                    transaction, availableBuys, transactionMatches);
            transactionMatches.addAll(newMatches);
            return List.copyOf(newMatches);
        }
        return List.of();
    }

    private void validateTradeParameters(Asset quantity, Asset price, Asset fee) {
        Objects.requireNonNull(quantity, "Quantity cannot be null");
        Objects.requireNonNull(price, "Price cannot be null");
        Objects.requireNonNull(fee, "Fee cannot be null");

        if (!isActive) {
            throw new IllegalStateException("Portfolio is not active");
        }

        if (!quantity.isPositive()) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        if (!price.isPositive()) {
            throw new IllegalArgumentException("Price must be positive");
        }
    }

    public void deactivate() {
        this.isActive = false;
    }

    /**
     * Verifica se o portfolio está elegível para execução de estratégia
     */
    public boolean isValid() {
        if (!this.isActive) {
            return false;
        }

        if (this.strategyId == null) {
            return false;
        }

        if (!hasMinimumCapitalForOperation() && !hasPosition()) {
            return false;
        }

        if (isInCooldownPeriod()) {
            return false;
        }

        return true;
    }

    private boolean hasMinimumCapitalForOperation() {
        return balance.getAvailable().amount().compareTo(MINIMUM_OPERATION_AMOUNT) >= 0;
    }

    private boolean isInCooldownPeriod() {
        if (lastExecutionTime == null) {
            return false;
        }

        Duration timeSinceLastExecution = Duration.between(lastExecutionTime, Instant.now());
        return timeSinceLastExecution.compareTo(EXECUTION_COOLDOWN) < 0;
    }

    /**
     * Cria PortfolioContext para ser passado para a Strategy.
     * O preço de mercado atual é fornecido pelo caller.
     */
    public PortfolioContextDto createContext(Asset currentMarketPrice) {
        return PortfolioContextDto.builder()
            .portfolioId(this.id)
            .portfolioName(this.name)
            .symbol(this.symbol)
            .totalCapital(this.balance.getTotal())
            .availableBalance(this.balance.getAvailable())
            .allocatedBalance(this.balance.getAllocated())
            .position(getPosition(currentMarketPrice))
            .openTransactions(getOpenBuyEntries())
            .pendingSellOrders(getPendingSellTransactions().stream()
                .map(PendingSellEntryDto::fromTransaction)
                .toList())
            .realizedPnL(this.balance.getRealizedPnL())
            .minimumOperationAmount(MINIMUM_OPERATION_AMOUNT)
            .maxExposurePerSymbol(DEFAULT_MAX_EXPOSURE_PERCENTAGE)
            .build();
    }

    /**
     * Atualiza timestamp da última execução
     */
    public void updateLastExecutionTime() {
        this.lastExecutionTime = Instant.now();
    }

    /**
     * Verifica se o portfolio pode receber market data de uma exchange específica
     */
    public boolean canReceiveMarketDataFrom(String exchangeName) {
        return allowedMarketDataSources == null ||
               allowedMarketDataSources.isEmpty() ||
               allowedMarketDataSources.contains(exchangeName.toUpperCase());
    }

    // ============================================================
    // Transaction Management Methods
    // ============================================================

    /**
     * Encontra transaction por clientOrderId
     */
    public Optional<Transaction> findTransactionByClientOrderId(String clientOrderId) {
        Objects.requireNonNull(clientOrderId, "ClientOrderId cannot be null");

        return transactions.stream()
                .filter(transaction -> clientOrderId.equals(transaction.clientOrderId()))
                .findFirst();
    }

    /**
     * Atualiza status de uma transaction existente
     */
    public void updateTransactionStatus(String clientOrderId, TransactionStatus newStatus) {
        Transaction transaction = findTransactionByClientOrderId(clientOrderId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Transaction not found for clientOrderId: " + clientOrderId));

        Transaction updated = transaction.withStatus(newStatus);

        transactions.remove(transaction);
        transactions.add(updated);
    }

    /**
     * Atualiza transaction com motivo de rejeição
     */
    public void updateTransactionAsRejected(String clientOrderId, String rejectReason) {
        Transaction transaction = findTransactionByClientOrderId(clientOrderId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Transaction not found for clientOrderId: " + clientOrderId));

        Transaction rejected = transaction
                .withRejectReason(rejectReason)
                .withStatus(TransactionStatus.REJECTED);

        transactions.remove(transaction);
        transactions.add(rejected);
    }

    /**
     * Atualiza transaction como executada e aplica mudanças no portfolio (balance e position).
     * @return matches criados pelo re-match (lista vazia se BUY)
     */
    public List<TransactionMatch> updateTransactionAsExecuted(
            String clientOrderId,
            TransactionStatus finalStatus,
            Asset executedQuantity,
            Asset executedPrice,
            Asset executedFee,
            Instant executedAt
    ) {
        if (!finalStatus.isExecuted()) {
            throw new IllegalArgumentException(
                    "Final status must be FILLED or PARTIALLY_FILLED, got: " + finalStatus);
        }

        Transaction transaction = findTransactionByClientOrderId(clientOrderId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Transaction not found for clientOrderId: " + clientOrderId));

        Transaction executed = transaction
                .withExecutedQuantity(executedQuantity)
                .withExecutedPrice(executedPrice)
                .withFee(executedFee)
                .withExecutedAt(executedAt)
                .withStatus(finalStatus);

        transactions.remove(transaction);
        transactions.add(executed);

        if (transaction.isBuy()) {
            executeBuyInternal(executedQuantity, executedPrice, executedFee);
            return List.of();
        } else {
            return executeSellInternal(executedQuantity, executedPrice, executedFee, executed.id());
        }
    }

    /**
     * Executa compra internamente (atualiza balance sem criar nova transaction)
     */
    private void executeBuyInternal(Asset quantity, Asset price, Asset fee) {
        validateTradeParameters(quantity, price, fee);

        BigDecimal totalAmount = quantity.amount().multiply(price.amount());
        Asset total = Asset.of(totalAmount, price.currency());
        Asset totalWithFee = total.add(fee);

        if (!balance.hasAvailableAmount(totalWithFee)) {
            throw new IllegalArgumentException("Insufficient balance for purchase");
        }

        balance.allocate(totalWithFee);
    }

    /**
     * Executa venda internamente — delega matching e cost para a AccountingPolicy.
     * @return matches criados pelo re-match
     */
    private List<TransactionMatch> executeSellInternal(Asset quantity, Asset price, Asset fee, UUID sellTransactionId) {
        validateTradeParameters(quantity, price, fee);

        Position currentPosition = getPosition(price);
        if (currentPosition == null) {
            throw new IllegalArgumentException("No position found for currency: " + this.symbol.value());
        }
        if (!currentPosition.canSell(quantity)) {
            throw new IllegalArgumentException("Insufficient quantity to sell");
        }

        // Re-match via policy with actually executed quantity
        Transaction sellTx = findTransactionById(sellTransactionId)
                .orElseThrow(() -> new IllegalStateException("Sell transaction not found: " + sellTransactionId));
        transactionMatches.removeIf(m -> m.sellTransactionId().equals(sellTransactionId));
        List<TransactionMatch> newMatches = accountingPolicy.matchOrder(
                sellTx, getOpenBuyTransactions(), transactionMatches);
        transactionMatches.addAll(newMatches);

        // Compute cost via policy
        BigDecimal costAmount = accountingPolicy.computeCost(sellTransactionId, transactions, transactionMatches);
        Asset cost = Asset.of(costAmount, price.currency());

        BigDecimal saleAmount = quantity.amount().multiply(price.amount());
        Asset saleValue = Asset.of(saleAmount, price.currency());
        Asset saleValueMinusFee = saleValue.subtract(fee);

        balance.realizeSale(cost, saleValueMinusFee);

        return List.copyOf(newMatches);
    }

    /**
     * Retorna lotes de compra abertos com quantidade disponível para a Strategy.
     * Desconta tanto matches confirmados (sells FILLED) quanto reservados (sells PENDING/SUBMITTED).
     * Exclui lotes sem quantidade livre.
     */
    public List<OpenBuyEntryDto> getOpenBuyEntries() {
        return transactions.stream()
                .filter(Transaction::isBuy)
                .filter(Transaction::isExecuted)
                .map(buy -> {
                    BigDecimal confirmed = getConfirmedMatchedQuantity(buy.id());
                    BigDecimal pending = getPendingMatchedQuantity(buy.id());
                    BigDecimal free = buy.getEffectiveQuantity().amount()
                            .subtract(confirmed).subtract(pending).max(BigDecimal.ZERO);
                    return OpenBuyEntryDto.builder()
                            .lotId(buy.id())
                            .executedQuantity(buy.getEffectiveQuantity())
                            .executedPrice(buy.getEffectivePrice())
                            .remainingQuantity(Asset.of(free, buy.getEffectiveQuantity().currency()))
                            .reservedQuantity(Asset.of(pending, buy.getEffectiveQuantity().currency()))
                            .executedAt(buy.executedAt())
                            .build();
                })
                .filter(dto -> dto.remainingQuantity().amount().compareTo(BigDecimal.ZERO) > 0)
                .toList();
    }

    /**
     * Lista compras executadas que ainda possuem quantidade remanescente (confirmed only).
     * Usado internamente pela AccountingPolicy para matching.
     */
    public List<Transaction> getOpenBuyTransactions() {
        return transactions.stream()
                .filter(Transaction::isBuy)
                .filter(Transaction::isExecuted)
                .filter(t -> getRemainingQuantity(t.id()).compareTo(BigDecimal.ZERO) > 0)
                .toList();
    }

    /**
     * Lista sells pendentes (PENDING ou SUBMITTED) em trânsito.
     */
    public List<Transaction> getPendingSellTransactions() {
        return transactions.stream()
                .filter(Transaction::isSell)
                .filter(t -> t.status() == TransactionStatus.PENDING
                          || t.status() == TransactionStatus.SUBMITTED)
                .toList();
    }

    // ============================================================
    // Transaction Match Methods (buy-sell linking)
    // ============================================================

    /**
     * Retorna quantidade remanescente de um BUY considerando apenas matches confirmados (sell FILLED).
     */
    public BigDecimal getRemainingQuantity(UUID buyTransactionId) {
        Transaction buyTx = findTransactionById(buyTransactionId).orElse(null);

        if (buyTx == null || buyTx.executedQuantity() == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal confirmed = getConfirmedMatchedQuantity(buyTransactionId);
        return buyTx.executedQuantity().amount().subtract(confirmed).max(BigDecimal.ZERO);
    }

    /**
     * Retorna quantidade matched com sells confirmados (FILLED/PARTIALLY_FILLED).
     */
    private BigDecimal getConfirmedMatchedQuantity(UUID buyTransactionId) {
        return transactionMatches.stream()
                .filter(m -> m.buyTransactionId().equals(buyTransactionId))
                .filter(m -> findTransactionById(m.sellTransactionId())
                        .map(Transaction::isExecuted).orElse(false))
                .map(TransactionMatch::matchedQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Retorna quantidade matched com sells pendentes (PENDING/SUBMITTED).
     */
    public BigDecimal getPendingMatchedQuantity(UUID buyTransactionId) {
        return transactionMatches.stream()
                .filter(m -> m.buyTransactionId().equals(buyTransactionId))
                .filter(m -> findTransactionById(m.sellTransactionId())
                        .map(t -> t.status() == TransactionStatus.PENDING
                                || t.status() == TransactionStatus.SUBMITTED)
                        .orElse(false))
                .map(TransactionMatch::matchedQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Remove todos os matches vinculados a um sell (cleanup em falha: CANCELED/REJECTED/EXPIRED).
     */
    public void removeMatchesForSell(UUID sellTransactionId) {
        transactionMatches.removeIf(m -> m.sellTransactionId().equals(sellTransactionId));
    }

    /**
     * Busca transaction por ID.
     */
    private Optional<Transaction> findTransactionById(UUID transactionId) {
        return transactions.stream()
                .filter(t -> t.id().equals(transactionId))
                .findFirst();
    }
}
