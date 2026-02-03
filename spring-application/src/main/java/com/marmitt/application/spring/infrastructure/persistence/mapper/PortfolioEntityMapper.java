package com.marmitt.application.spring.infrastructure.persistence.mapper;

import com.marmitt.application.spring.infrastructure.persistence.entity.BalanceEntity;
import com.marmitt.application.spring.infrastructure.persistence.entity.MarketDataSourceRef;
import com.marmitt.application.spring.infrastructure.persistence.entity.PortfolioEntity;
import com.marmitt.application.spring.infrastructure.persistence.entity.PositionEntity;
import com.marmitt.application.spring.infrastructure.persistence.entity.TransactionEntity;
import com.marmitt.core.domain.Symbol;
import com.marmitt.core.domain.portfolio.Asset;
import com.marmitt.core.domain.portfolio.Balance;
import com.marmitt.core.domain.portfolio.Portfolio;
import com.marmitt.core.domain.portfolio.Position;
import com.marmitt.core.domain.portfolio.Transaction;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class PortfolioEntityMapper {

    // ==================== Domain → Entity ====================

    public static PortfolioEntity toPortfolioEntity(Portfolio domain) {
        return PortfolioEntity.builder()
                .id(domain.getId())
                .name(domain.getName())
                .strategyId(domain.getStrategyId())
                .strategyName(domain.getStrategyName())
                .symbol(domain.getSymbol().value())
                .initialCapitalAmount(domain.getBalance().getInitialCapital().amount())
                .initialCapitalCurrency(domain.getBalance().getInitialCapital().currency())
                .orderExecutionExchange(domain.getOrderExecutionExchange())
                .isActive(domain.isActive())
                .createdAt(domain.getCreatedAt())
                .allowedMarketDataSources(
                        domain.getAllowedMarketDataSources() != null
                                ? domain.getAllowedMarketDataSources().stream()
                                    .map(MarketDataSourceRef::new)
                                    .collect(Collectors.toSet())
                                : new HashSet<>()
                )
                .build();
    }

    public static BalanceEntity toBalanceEntity(UUID portfolioId, Balance balance, Instant lastExecutionTime) {
        return BalanceEntity.builder()
                .portfolioId(portfolioId)
                .availableAmount(balance.getAvailable().amount())
                .availableCurrency(balance.getAvailable().currency())
                .investedAmount(balance.getInvested().amount())
                .investedCurrency(balance.getInvested().currency())
                .realizedPnL(balance.getRealizedPnL())
                .lastExecutionTime(lastExecutionTime)
                .updatedAt(Instant.now())
                .build();
    }

    public static PositionEntity toPositionEntity(UUID portfolioId, Position position, Instant existingOpenedAt) {
        return PositionEntity.builder()
                .portfolioId(portfolioId)
                .symbol(position.getSymbol().value())
                .quantityAmount(position.getQuantity().amount())
                .quantityCurrency(position.getQuantity().currency())
                .averagePriceAmount(position.getAveragePrice().amount())
                .averagePriceCurrency(position.getAveragePrice().currency())
                .currentPriceAmount(position.getCurrentPrice().amount())
                .currentPriceCurrency(position.getCurrentPrice().currency())
                .openedAt(existingOpenedAt != null ? existingOpenedAt : Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    public static TransactionEntity toTransactionEntity(UUID portfolioId, Transaction domain) {
        TransactionEntity.TransactionEntityBuilder builder = TransactionEntity.builder()
                .id(domain.id())
                .portfolioId(portfolioId)
                .clientOrderId(domain.clientOrderId())
                .status(domain.status())
                .type(domain.type())
                .symbol(domain.symbol().value())
                .quantityAmount(domain.quantity().amount())
                .quantityCurrency(domain.quantity().currency())
                .quantityAssetType(domain.quantity().type())
                .priceAmount(domain.price().amount())
                .priceCurrency(domain.price().currency())
                .priceAssetType(domain.price().type())
                .totalAmount(domain.total().amount())
                .totalCurrency(domain.total().currency())
                .totalAssetType(domain.total().type())
                .feeAmount(domain.fee().amount())
                .feeCurrency(domain.fee().currency())
                .feeAssetType(domain.fee().type())
                .requestedAt(domain.requestedAt())
                .executedAt(domain.executedAt())
                .rejectReason(domain.rejectReason());

        if (domain.executedQuantity() != null) {
            builder.executedQuantityAmount(domain.executedQuantity().amount())
                    .executedQuantityCurrency(domain.executedQuantity().currency())
                    .executedQuantityAssetType(domain.executedQuantity().type());
        }

        if (domain.executedPrice() != null) {
            builder.executedPriceAmount(domain.executedPrice().amount())
                    .executedPriceCurrency(domain.executedPrice().currency())
                    .executedPriceAssetType(domain.executedPrice().type());
        }

        return builder.build();
    }

    // ==================== Entity → Domain ====================

    public static Portfolio toDomain(
            PortfolioEntity portfolioEntity,
            BalanceEntity balanceEntity,
            PositionEntity positionEntity,
            List<TransactionEntity> transactionEntities
    ) {
        Asset initialCapital = Asset.of(
                portfolioEntity.getInitialCapitalAmount(),
                portfolioEntity.getInitialCapitalCurrency()
        );

        Set<String> marketDataSources = portfolioEntity.getAllowedMarketDataSources() != null
                ? portfolioEntity.getAllowedMarketDataSources().stream()
                    .map(MarketDataSourceRef::source)
                    .collect(Collectors.toSet())
                : null;

        Portfolio portfolio = new Portfolio(
                portfolioEntity.getId(),
                portfolioEntity.getName(),
                portfolioEntity.getStrategyId(),
                portfolioEntity.getStrategyName(),
                Symbol.of(portfolioEntity.getSymbol()),
                initialCapital,
                portfolioEntity.getOrderExecutionExchange(),
                marketDataSources == null || marketDataSources.isEmpty() ? null : marketDataSources
        );

        // Restore balance
        if (balanceEntity != null) {
            restoreBalanceState(portfolio, balanceEntity);
        }

        // Restore position
        if (positionEntity != null) {
            restorePosition(portfolio, positionEntity);
        }

        // Restore transactions
        if (transactionEntities != null && !transactionEntities.isEmpty()) {
            List<Transaction> transactions = transactionEntities.stream()
                    .map(PortfolioEntityMapper::toTransactionDomain)
                    .toList();
            restoreTransactions(portfolio, transactions);
        }

        // Restore state
        if (!portfolioEntity.isActive()) {
            portfolio.deactivate();
        }
        setPrivateField(portfolio, "createdAt", portfolioEntity.getCreatedAt());
        if (balanceEntity != null) {
            setPrivateField(portfolio, "lastExecutionTime", balanceEntity.getLastExecutionTime());
        }

        return portfolio;
    }

    private static void restoreBalanceState(Portfolio portfolio, BalanceEntity entity) {
        Balance balance = portfolio.getBalance();

        Asset available = Asset.of(entity.getAvailableAmount(), entity.getAvailableCurrency());
        Asset invested = Asset.of(entity.getInvestedAmount(), entity.getInvestedCurrency());

        setPrivateField(balance, "available", available);
        setPrivateField(balance, "invested", invested);
        setPrivateField(balance, "realizedPnL", entity.getRealizedPnL());
    }

    private static void restorePosition(Portfolio portfolio, PositionEntity entity) {
        Asset quantity = Asset.of(entity.getQuantityAmount(), entity.getQuantityCurrency());
        Asset avgPrice = Asset.of(entity.getAveragePriceAmount(), entity.getAveragePriceCurrency());

        Position position = Position.create(Symbol.of(entity.getSymbol()), quantity, avgPrice);

        if (entity.getCurrentPriceAmount() != null) {
            Asset currentPrice = Asset.of(entity.getCurrentPriceAmount(), entity.getCurrentPriceCurrency());
            position.updateCurrentPrice(currentPrice);
        }

        setPrivateField(portfolio, "position", position);
    }

    private static void restoreTransactions(Portfolio portfolio, List<Transaction> transactions) {
        List<Transaction> portfolioTransactions = getPrivateField(portfolio, "transactions");
        portfolioTransactions.clear();
        portfolioTransactions.addAll(transactions);
    }

    private static Transaction toTransactionDomain(TransactionEntity entity) {
        Asset executedQuantity = entity.getExecutedQuantityAmount() != null
                ? new Asset(entity.getExecutedQuantityAmount(), entity.getExecutedQuantityCurrency(), entity.getExecutedQuantityAssetType())
                : null;

        Asset executedPrice = entity.getExecutedPriceAmount() != null
                ? new Asset(entity.getExecutedPriceAmount(), entity.getExecutedPriceCurrency(), entity.getExecutedPriceAssetType())
                : null;

        return Transaction.builder()
                .id(entity.getId())
                .clientOrderId(entity.getClientOrderId())
                .status(entity.getStatus())
                .type(entity.getType())
                .symbol(Symbol.of(entity.getSymbol()))
                .quantity(new Asset(entity.getQuantityAmount(), entity.getQuantityCurrency(), entity.getQuantityAssetType()))
                .executedQuantity(executedQuantity)
                .price(new Asset(entity.getPriceAmount(), entity.getPriceCurrency(), entity.getPriceAssetType()))
                .executedPrice(executedPrice)
                .total(new Asset(entity.getTotalAmount(), entity.getTotalCurrency(), entity.getTotalAssetType()))
                .fee(new Asset(entity.getFeeAmount(), entity.getFeeCurrency(), entity.getFeeAssetType()))
                .requestedAt(entity.getRequestedAt())
                .executedAt(entity.getExecutedAt())
                .rejectReason(entity.getRejectReason())
                .build();
    }

    @SuppressWarnings("unchecked")
    private static <T> T getPrivateField(Object obj, String fieldName) {
        try {
            Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return (T) field.get(obj);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get field: " + fieldName, e);
        }
    }

    private static void setPrivateField(Object obj, String fieldName, Object value) {
        try {
            Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(obj, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field: " + fieldName, e);
        }
    }
}
