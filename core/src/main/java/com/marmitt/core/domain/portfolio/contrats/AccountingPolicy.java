package com.marmitt.core.domain.portfolio.contrats;

import com.marmitt.core.domain.Symbol;
import com.marmitt.core.domain.portfolio.Asset;
import com.marmitt.core.domain.portfolio.Position;
import com.marmitt.core.domain.portfolio.Transaction;
import com.marmitt.core.domain.portfolio.TransactionMatch;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface AccountingPolicy {
    /**
     * Calcula a visão consolidada da posição (estoque) baseada nas transações e matches.
     */
    Position calculatePosition(Symbol symbol, List<Transaction> transactions,
                               List<TransactionMatch> transactionMatches, Asset currentPrice);

    /**
     * Define como uma nova venda deve ser "abatida" contra as compras existentes.
     * @param sellTransaction A transação de venda atual
     * @param availableBuys Lista de transações de compra que ainda têm saldo
     * @param existingMatches Matches já existentes no portfólio para consistência
     */
    List<TransactionMatch> matchOrder(Transaction sellTransaction, List<Transaction> availableBuys,
                                      List<TransactionMatch> existingMatches);

    /**
     * Computa cost basis a partir dos lotes realmente consumidos por um sell.
     */
    BigDecimal computeCost(UUID sellTransactionId, List<Transaction> transactions,
                           List<TransactionMatch> transactionMatches);
}
