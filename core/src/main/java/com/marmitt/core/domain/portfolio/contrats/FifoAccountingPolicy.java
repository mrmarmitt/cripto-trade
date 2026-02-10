package com.marmitt.core.domain.portfolio.contrats;

import com.marmitt.core.domain.Symbol;
import com.marmitt.core.domain.portfolio.Asset;
import com.marmitt.core.domain.portfolio.Position;
import com.marmitt.core.domain.portfolio.Transaction;
import com.marmitt.core.domain.portfolio.TransactionMatch;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class FifoAccountingPolicy implements AccountingPolicy {

    private static final BigDecimal DUST_THRESHOLD = new BigDecimal("0.00000001");

    @Override
    public Position calculatePosition(Symbol symbol, List<Transaction> transactions,
                                      List<TransactionMatch> transactionMatches, Asset currentPrice) {
        // Filtra apenas compras executadas
        List<Transaction> buyTransactions = transactions.stream()
                .filter(Transaction::isBuy)
                .filter(Transaction::isExecuted)
                .toList();

        BigDecimal totalQuantity = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;

        for (Transaction buy : buyTransactions) {
            BigDecimal remainingQty = calculateAvailable(buy, transactionMatches);

            if (remainingQty.compareTo(BigDecimal.ZERO) > 0) {
                totalQuantity = totalQuantity.add(remainingQty);
                totalCost = totalCost.add(remainingQty.multiply(buy.getEffectivePrice().amount()));
            }
        }

        if (totalQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            return new Position(symbol, Asset.crypto(BigDecimal.ZERO, symbol.getBaseAsset()), currentPrice);
        }

        BigDecimal avgPrice = totalCost.divide(totalQuantity, 8, RoundingMode.HALF_UP);

        Position position = new Position(
                symbol,
                Asset.crypto(totalQuantity, symbol.getBaseAsset()),
                Asset.of(avgPrice, symbol.getQuoteAsset())
        );
        position.updateCurrentPrice(currentPrice);
        return position;
    }

    @Override
    public List<TransactionMatch> matchOrder(Transaction sellTransaction, List<Transaction> availableBuys,
                                             List<TransactionMatch> existingMatches) {
        List<TransactionMatch> newMatches = new ArrayList<>();
        BigDecimal remainingToMatch = sellTransaction.getEffectiveQuantity().amount();

        // 1. Prioridade: Se a transação tem um targetLotId
        if (sellTransaction.targetLotId() != null) {
            Transaction targetBuy = availableBuys.stream()
                    .filter(b -> b.id().equals(sellTransaction.targetLotId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Target lot not found: " + sellTransaction.targetLotId()));

            BigDecimal buyAvailable = calculateAvailable(targetBuy, existingMatches);
            BigDecimal matchAmount = remainingToMatch.min(buyAvailable);

            newMatches.add(new TransactionMatch(targetBuy.id(), sellTransaction.id(), matchAmount));
            remainingToMatch = remainingToMatch.subtract(matchAmount);
        }

        // 2. Se ainda sobrar quantidade (ou não tiver targetId), segue FIFO normal
        if (remainingToMatch.compareTo(BigDecimal.ZERO) > 0) {
            // Combina existingMatches + newMatches para que o FIFO enxergue o que já foi matched acima
            List<TransactionMatch> allMatches = new ArrayList<>(existingMatches);
            allMatches.addAll(newMatches);

            List<Transaction> sortedBuys = availableBuys.stream()
                    .sorted(Comparator.comparing(Transaction::executedAt))
                    .toList();

            for (Transaction buy : sortedBuys) {
                if (remainingToMatch.compareTo(BigDecimal.ZERO) <= 0) break;

                BigDecimal buyAvailable = calculateAvailable(buy, allMatches);
                if (buyAvailable.compareTo(BigDecimal.ZERO) <= 0) continue;

                BigDecimal matchAmount = remainingToMatch.min(buyAvailable);
                newMatches.add(new TransactionMatch(buy.id(), sellTransaction.id(), matchAmount));
                allMatches.add(newMatches.getLast());
                remainingToMatch = remainingToMatch.subtract(matchAmount);
            }
        }

        return newMatches;
    }

    @Override
    public BigDecimal computeCost(UUID sellTransactionId, List<Transaction> transactions,
                                  List<TransactionMatch> transactionMatches) {
        return transactionMatches.stream()
                .filter(m -> m.sellTransactionId().equals(sellTransactionId))
                .map(m -> {
                    Transaction buy = transactions.stream()
                            .filter(t -> t.id().equals(m.buyTransactionId()))
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException(
                                    "Buy transaction not found for match: " + m.buyTransactionId()));
                    return m.matchedQuantity().multiply(buy.executedPrice().amount());
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateAvailable(Transaction buy, List<TransactionMatch> existingMatches) {
        BigDecimal matched = existingMatches.stream()
                .filter(m -> m.buyTransactionId().equals(buy.id()))
                .map(TransactionMatch::matchedQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal available = buy.getEffectiveQuantity().amount().subtract(matched);
        return available.compareTo(DUST_THRESHOLD) < 0 ? BigDecimal.ZERO : available;
    }
}
