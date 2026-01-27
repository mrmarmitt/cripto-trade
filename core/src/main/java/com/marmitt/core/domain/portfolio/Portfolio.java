package com.marmitt.core.domain.portfolio;

import com.marmitt.core.domain.Symbol;
import com.marmitt.core.dto.strategy.OpenBuyEntryDto;
import com.marmitt.core.dto.strategy.PortfolioContextDto;
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
    private final UUID strategyId;
    private final String strategyName;
    private final Symbol symbol;
    private final Balance balance;
    private Position position;
    private final List<Transaction> transactions;
    private boolean isActive;
    private final Instant createdAt;
    private Instant lastExecutionTime;

    // Exchange configuration
    private final String orderExecutionExchange;  // Exchange onde ordens são executadas
    private final Set<String> allowedMarketDataSources;  // Exchanges permitidas para market data (null = todas)
    
    // Configurações de limitação
    private static final BigDecimal MINIMUM_OPERATION_AMOUNT = new BigDecimal("10.00"); // $10 USD
    private static final Duration EXECUTION_COOLDOWN = Duration.ofSeconds(0); // 30 segundos entre execuções
    private static final BigDecimal DEFAULT_MAX_EXPOSURE_PERCENTAGE = new BigDecimal("0.20"); // 20% por símbolo
    
    public Portfolio(
            UUID id,
            String name,
            UUID strategyId,
            String strategyName,
            Symbol symbol,
            Asset initialCapital,
            String orderExecutionExchange,
            Set<String> allowedMarketDataSources
    ) {
        this.id = id;
        this.name = name;
        this.strategyId = strategyId;
        this.strategyName = strategyName;
        this.symbol = Objects.requireNonNull(symbol, "Symbol cannot be null");
        this.balance = Balance.withInitialCapital(initialCapital);
        this.position = null; // No initial position
        this.transactions = new ArrayList<>();
        this.isActive = true;
        this.createdAt = Instant.now();
        this.lastExecutionTime = null;
        this.orderExecutionExchange = Objects.requireNonNull(orderExecutionExchange, "Order execution exchange cannot be null");
        this.allowedMarketDataSources = allowedMarketDataSources != null ?
                Collections.unmodifiableSet(allowedMarketDataSources) : null;
    }

    public void executeBuy(Asset quantity, Asset price, Asset fee) {
        validateTradeParameters(quantity, price, fee);

        // Total em quote currency (USDT): quantidade × preço
        BigDecimal totalAmount = quantity.amount().multiply(price.amount());
        Asset total = Asset.of(totalAmount, price.currency());
        Asset totalWithFee = total.add(fee);
        
        if (!balance.hasAvailableAmount(totalWithFee)) {
            throw new IllegalArgumentException("Insufficient balance for purchase");
        }
        
        // Update balance
        balance.allocate(totalWithFee);
        
        // Update or create position
        if (position != null) {
            position.updatePosition(quantity, price);
        } else {
            position = Position.create(this.symbol, quantity, price);
        }
        
        // Record transaction
        Transaction transaction = Transaction.builder()
                .type(com.marmitt.core.enums.TransactionType.BUY)
                .symbol(this.symbol)
                .quantity(quantity)
                .price(price)
                .total(total)
                .fee(fee)
                .build();
        
        transactions.add(transaction);
    }
    
    public void executeSell(Asset quantity, Asset price, Asset fee) {
        validateTradeParameters(quantity, price, fee);

        if (position == null) {
            throw new IllegalArgumentException("No position found for currency: " + this.symbol.value());
        }

        if (!position.canSell(quantity)) {
            throw new IllegalArgumentException("Insufficient quantity to sell");
        }

        // Calcular custo original (quantity × averagePrice) - ANTES de reduzir position
        BigDecimal costAmount = quantity.amount().multiply(position.getAveragePrice().amount());
        Asset cost = Asset.of(costAmount, price.currency());  // Usar quote currency (USDT)

        // Calcular valor de venda (quantity × salePrice)
        BigDecimal saleAmount = quantity.amount().multiply(price.amount());
        Asset saleValue = Asset.of(saleAmount, price.currency());

        // Subtrair fee do valor de venda
        Asset saleValueMinusFee = saleValue.subtract(fee);

        // Update position
        position.reducePosition(quantity);
        if (position.isEmpty()) {
            position = null; // Clear empty position
        }

        // Update balance - remove custo do invested, adiciona valor de venda ao available
        balance.realizeSale(cost, saleValueMinusFee);

        // Record transaction
        Transaction transaction = Transaction.builder()
                .type(com.marmitt.core.enums.TransactionType.SELL)
                .symbol(this.symbol)
                .quantity(quantity)
                .price(price)
                .total(saleValue)
                .fee(fee)
                .build();

        transactions.add(transaction);
    }
    
    public void updatePositionPrice(Asset newPrice) {
        if (position != null) {
            position.updateCurrentPrice(newPrice);
        }
    }
    
    public boolean hasPosition() {
        return position != null && !position.isEmpty();
    }
    
    public Asset getCurrentPositionValue() {
        return hasPosition() ? position.getCurrentValue() : 
               Asset.fiat(BigDecimal.ZERO, balance.getAvailable().currency());
    }
    
    public BigDecimal getCurrentPositionQuantity() {
        return hasPosition() ? position.getQuantity().amount() : BigDecimal.ZERO;
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

    public void activate() {
        this.isActive = true;
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

        // 6. Deve respeitar cooldown entre execuções
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
            return false; // Primeira execução
        }
        
        Duration timeSinceLastExecution = Duration.between(lastExecutionTime, Instant.now());
        return timeSinceLastExecution.compareTo(EXECUTION_COOLDOWN) < 0;
    }
    
    /**
     * Cria PortfolioContext para ser passado para a Strategy
     * Contém todos os dados necessários para que a Strategy possa calcular quantities
     */
    public PortfolioContextDto createContext() {
        return PortfolioContextDto.builder()
            .portfolioId(this.id)
            .portfolioName(this.name)
            .symbol(this.symbol)
            .totalCapital(this.balance.getTotal())
            .availableBalance(this.balance.getAvailable())
            .allocatedBalance(this.balance.getAllocated())
            .position(this.position)
            .openTransactions(getOpenBuyTransactions().stream()
                .map(OpenBuyEntryDto::fromTransaction)
                .toList())
            .realizedPnL(this.balance.getRealizedPnL())
            .minimumOperationAmount(MINIMUM_OPERATION_AMOUNT)
            .maxExposurePerSymbol(DEFAULT_MAX_EXPOSURE_PERCENTAGE)
            .build();
    }
    
    /**
     * Atualiza timestamp da última execução
     * Deve ser chamado após execução bem-sucedida da estratégia
     */
    public void updateLastExecutionTime() {
        this.lastExecutionTime = Instant.now();
    }

    /**
     * Verifica se o portfolio pode receber market data de uma exchange específica
     * @param exchangeName nome da exchange
     * @return true se permitido, false caso contrário
     */
    public boolean canReceiveMarketDataFrom(String exchangeName) {
        // Se allowedMarketDataSources for null ou vazio, aceita de todas as exchanges
        return allowedMarketDataSources == null ||
               allowedMarketDataSources.isEmpty() ||
               allowedMarketDataSources.contains(exchangeName.toUpperCase());
    }

    // ============================================================
    // Transaction Management Methods
    // ============================================================

    /**
     * Adiciona transaction pendente (antes de enviar ordem para exchange)
     */
    public void addPendingTransaction(Transaction transaction) {
        Objects.requireNonNull(transaction, "Transaction cannot be null");

        if (transaction.status() != TransactionStatus.PENDING) {
            throw new IllegalArgumentException("Transaction must be PENDING status");
        }

        transactions.add(transaction);
    }

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

        // Transaction é imutável - criar nova com status atualizado
        Transaction updated = transaction.withStatus(newStatus);

        // Substituir transaction antiga pela atualizada
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

        // withStatus() deve ser chamado por último para que a validação encontre rejectReason preenchido
        Transaction rejected = transaction
                .withRejectReason(rejectReason)
                .withStatus(TransactionStatus.REJECTED);

        transactions.remove(transaction);
        transactions.add(rejected);
    }

    /**
     * Atualiza transaction como executada e aplica mudanças no portfolio (balance e position)
     */
    public void updateTransactionAsExecuted(
            String clientOrderId,
            TransactionStatus finalStatus,
            Asset executedQuantity,
            Asset executedPrice,
            Asset executedFee,
            Instant executedAt
    ) {
        // Validar status
        if (!finalStatus.isExecuted()) {
            throw new IllegalArgumentException(
                    "Final status must be FILLED or PARTIALLY_FILLED, got: " + finalStatus);
        }

        // Buscar transaction
        Transaction transaction = findTransactionByClientOrderId(clientOrderId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Transaction not found for clientOrderId: " + clientOrderId));

        // Criar versão atualizada com dados de execução
        // withStatus() deve ser chamado por último para que a validação encontre os campos preenchidos
        Transaction executed = transaction
                .withExecutedQuantity(executedQuantity)
                .withExecutedPrice(executedPrice)
                .withFee(executedFee)
                .withExecutedAt(executedAt)
                .withStatus(finalStatus);

        // Substituir transaction
        transactions.remove(transaction);
        transactions.add(executed);

        // Atualizar balance e position baseado no tipo
        if (transaction.isBuy()) {
            executeBuyInternal(executedQuantity, executedPrice, executedFee);
        } else {
            executeSellInternal(executedQuantity, executedPrice, executedFee);
        }
    }

    /**
     * Executa compra internamente (atualiza balance e position sem criar nova transaction)
     * Usado por updateTransactionAsExecuted()
     */
    private void executeBuyInternal(Asset quantity, Asset price, Asset fee) {
        validateTradeParameters(quantity, price, fee);

        // Total em quote currency (USDT): quantidade × preço
        BigDecimal totalAmount = quantity.amount().multiply(price.amount());
        Asset total = Asset.of(totalAmount, price.currency());
        Asset totalWithFee = total.add(fee);

        if (!balance.hasAvailableAmount(totalWithFee)) {
            throw new IllegalArgumentException("Insufficient balance for purchase");
        }

        // Update balance
        balance.allocate(totalWithFee);

        // Update or create position
        if (position != null) {
            position.updatePosition(quantity, price);
        } else {
            position = Position.create(this.symbol, quantity, price);
        }
    }

    /**
     * Executa venda internamente (atualiza balance e position sem criar nova transaction)
     * Usado por updateTransactionAsExecuted()
     */
    private void executeSellInternal(Asset quantity, Asset price, Asset fee) {
        validateTradeParameters(quantity, price, fee);

        if (position == null) {
            throw new IllegalArgumentException("No position found for currency: " + this.symbol.value());
        }

        if (!position.canSell(quantity)) {
            throw new IllegalArgumentException("Insufficient quantity to sell");
        }

        // Calcular custo original (quantity × averagePrice) - ANTES de reduzir position
        BigDecimal costAmount = quantity.amount().multiply(position.getAveragePrice().amount());
        Asset cost = Asset.of(costAmount, price.currency());  // Usar quote currency (USDT)

        // Calcular valor de venda (quantity × salePrice)
        BigDecimal saleAmount = quantity.amount().multiply(price.amount());
        Asset saleValue = Asset.of(saleAmount, price.currency());

        // Subtrair fee do valor de venda
        Asset saleValueMinusFee = saleValue.subtract(fee);

        // Update position
        position.reducePosition(quantity);
        if (position.isEmpty()) {
            position = null; // Clear empty position
        }

        // Update balance - remove custo do invested, adiciona valor de venda ao available
        balance.realizeSale(cost, saleValueMinusFee);
    }

    /**
     * Lista compras executadas que representam posições abertas
     */
    public List<Transaction> getOpenBuyTransactions() {
        return transactions.stream()
                .filter(Transaction::isBuy)
                .filter(Transaction::isExecuted)
                .toList();
    }

    /**
     * Lista todas as transactions com sucesso (FILLED)
     */
    public List<Transaction> getSuccessfulTransactions() {
        return transactions.stream()
                .filter(transaction -> transaction.status() == TransactionStatus.FILLED)
                .toList();
    }

    /**
     * Lista transactions pendentes (PENDING ou SUBMITTED)
     */
    public List<Transaction> getPendingTransactions() {
        return transactions.stream()
                .filter(transaction -> transaction.status() == TransactionStatus.PENDING ||
                        transaction.status() == TransactionStatus.SUBMITTED)
                .toList();
    }

    /**
     * Lista transactions que falharam (REJECTED, CANCELED, EXPIRED)
     */
    public List<Transaction> getFailedTransactions() {
        return transactions.stream()
                .filter(Transaction::isFailed)
                .toList();
    }
}