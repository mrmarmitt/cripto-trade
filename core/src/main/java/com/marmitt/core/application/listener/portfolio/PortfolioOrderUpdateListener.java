package com.marmitt.core.application.listener.portfolio;

import com.marmitt.core.domain.portfolio.Asset;
import com.marmitt.core.domain.portfolio.Portfolio;
import com.marmitt.core.domain.portfolio.Transaction;
import com.marmitt.core.domain.portfolio.TransactionMatch;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.enums.TransactionStatus;
import com.marmitt.core.ports.outbound.listener.OrderUpdateListener;
import com.marmitt.core.ports.outbound.repository.PortfolioRepositoryPort;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Listener responsável por processar atualizações de ordens executadas.
 * Atualiza o estado do Portfolio (balance, position, transaction status) quando
 * uma ordem é confirmada (FILLED, CANCELED, REJECTED, etc).
 *
 * Independente do PortfolioStrategyListener - comunicação via clientOrderId no PortfolioRepository.
 */
@Slf4j
public class PortfolioOrderUpdateListener implements OrderUpdateListener {

    private final PortfolioRepositoryPort portfolioRepository;

    public PortfolioOrderUpdateListener(PortfolioRepositoryPort portfolioRepository) {
        this.portfolioRepository = portfolioRepository;
    }

    @Override
    public void onOrderUpdate(OrderDataDto orderData) {
        log.info(">>> PortfolioOrderUpdateListener received order update - OrderId: {}, ClientOrderId: {}, Status: {}",
                orderData.orderId(), orderData.clientOrderId(), orderData.status());

        String clientOrderId = orderData.clientOrderId();

        if (clientOrderId == null || clientOrderId.isBlank()) {
            log.warn("Received order update without clientOrderId - OrderId: {}", orderData.orderId());
            return;
        }

        // Buscar portfolio pela transaction (usando clientOrderId)
        log.debug("Searching for portfolio with clientOrderId: {}", clientOrderId);
        Portfolio portfolio = findPortfolioByClientOrderId(clientOrderId);

        if (portfolio == null) {
            log.warn("Portfolio not found for clientOrderId: {} - Total portfolios in repository: {}",
                    clientOrderId, portfolioRepository.findAll().size());
            // Debug: listar transactions de todos os portfolios
            portfolioRepository.findAll().forEach(p -> {
                log.debug("Portfolio {} has {} transactions: {}",
                        p.getName(),
                        p.getTransactions().size(),
                        p.getTransactions().stream()
                                .map(transaction -> transaction.clientOrderId() + "=" + transaction.status())
                                .toList());
            });
            return;
        }

        log.debug("Found portfolio: {} for clientOrderId: {}", portfolio.getName(), clientOrderId);

        log.debug("Processing order update for Portfolio: {} - ClientOrderId: {}, Status: {}",
                portfolio.getName(), clientOrderId, orderData.status());

        // Roteamento baseado no status
        switch (orderData.status()) {
            case OrderDataDto.OrderStatus.FILLED, OrderDataDto.OrderStatus.PARTIALLY_FILLED ->
                    handleExecutedOrder(portfolio, orderData);
            case OrderDataDto.OrderStatus.CANCELED ->
                    handleCanceledOrder(portfolio, orderData);
            case OrderDataDto.OrderStatus.REJECTED ->
                    handleRejectedOrder(portfolio, orderData);
            case OrderDataDto.OrderStatus.EXPIRED ->
                    handleExpiredOrder(portfolio, orderData);
            default -> {
                log.debug("Order update for non-final status - Portfolio: {}, Status: {}",
                        portfolio.getName(), orderData.status());
                // Estados SUBMITTED, ACCEPTED - apenas atualizar status
                // Não precisa fazer nada além de log
            }
        }
    }

    /**
     * Busca portfolio que contém transaction com o clientOrderId
     */
    private Portfolio findPortfolioByClientOrderId(String clientOrderId) {
        // Buscar em todos os portfolios (poderia otimizar com um índice)
        return portfolioRepository.findAll().stream()
                .filter(p -> p.findTransactionByClientOrderId(clientOrderId).isPresent())
                .findFirst()
                .orElse(null);
    }

    /**
     * Processa ordem executada (FILLED ou PARTIALLY_FILLED)
     */
    private void handleExecutedOrder(Portfolio portfolio, OrderDataDto orderData) {
        log.info("Processing FILLED order - Portfolio: {}, Symbol: {}, Side: {}, Quantity: {}, Price: {}",
                portfolio.getName(), orderData.symbol(), orderData.side(),
                orderData.executedQuantity(), orderData.executedPrice());

        try {
            TransactionStatus status = orderData.status() == OrderDataDto.OrderStatus.FILLED ?
                    TransactionStatus.FILLED : TransactionStatus.PARTIALLY_FILLED;

            BigDecimal fee = orderData.fee() != null ? orderData.fee() : BigDecimal.ZERO;

            // Buscar moeda base do symbol para criar Asset corretamente
            String baseCurrency = portfolio.getSymbol().getBaseAsset();
            String quoteCurrency = portfolio.getSymbol().getQuoteAsset();

            Asset executedQuantity = Asset.of(orderData.executedQuantity(), baseCurrency);
            Asset executedPrice = Asset.of(orderData.executedPrice(), quoteCurrency);
            Asset executedFee = Asset.of(fee, quoteCurrency);

            // Atualizar transaction e portfolio (balance + position) — retorna matches do re-match
            List<TransactionMatch> newMatches = portfolio.updateTransactionAsExecuted(
                    orderData.clientOrderId(),
                    status,
                    executedQuantity,
                    executedPrice,
                    executedFee,
                    Instant.now()
            );

            Transaction executedTransaction = portfolio.findTransactionByClientOrderId(orderData.clientOrderId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Transaction not found after execution update: " + orderData.clientOrderId()));

            // Deletar matches provisórios antigos antes de salvar os novos (re-match com executedQuantity)
            if (executedTransaction.isSell()) {
                portfolioRepository.deleteMatchesBySellTransactionId(executedTransaction.id());
            }

            portfolioRepository.saveTradeExecution(
                    portfolio.getId(),
                    portfolio.getBalance(),
                    portfolio.getLastExecutionTime(),
                    portfolio.getPosition(executedPrice),
                    executedTransaction,
                    newMatches
            );

            log.info("Portfolio updated after FILLED order - Portfolio: {}, Available: {}",
                    portfolio.getName(),
                    portfolio.getBalance().getAvailable().amount());

        } catch (Exception e) {
            log.error("Error handling order execution - Portfolio: {}, ClientOrderId: {}, Error: {}",
                    portfolio.getName(), orderData.clientOrderId(), e.getMessage(), e);
        }
    }

    /**
     * Processa ordem cancelada
     */
    private void handleCanceledOrder(Portfolio portfolio, OrderDataDto orderData) {
        log.info("Processing CANCELED order - Portfolio: {}, ClientOrderId: {}",
                portfolio.getName(), orderData.clientOrderId());

        try {
            // Cleanup: remover matches provisórios se for SELL
            cleanupMatchesForFailedSell(portfolio, orderData.clientOrderId());

            portfolio.updateTransactionStatus(orderData.clientOrderId(), TransactionStatus.CANCELED);
            Transaction canceledTransaction = portfolio.findTransactionByClientOrderId(orderData.clientOrderId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Transaction not found after cancel update: " + orderData.clientOrderId()));
            portfolioRepository.saveTransaction(portfolio.getId(), canceledTransaction);

            log.info("Transaction marked as CANCELED - Portfolio: {}", portfolio.getName());

        } catch (Exception e) {
            log.error("Error handling order cancellation - Portfolio: {}, ClientOrderId: {}, Error: {}",
                    portfolio.getName(), orderData.clientOrderId(), e.getMessage(), e);
        }
    }

    /**
     * Processa ordem rejeitada
     */
    private void handleRejectedOrder(Portfolio portfolio, OrderDataDto orderData) {
        log.warn("Processing REJECTED order - Portfolio: {}, ClientOrderId: {}, Reason: {}",
                portfolio.getName(), orderData.clientOrderId(), orderData.rejectReason());

        try {
            // Cleanup: remover matches provisórios se for SELL
            cleanupMatchesForFailedSell(portfolio, orderData.clientOrderId());

            String reason = orderData.rejectReason() != null ? orderData.rejectReason() : "Unknown reason";
            portfolio.updateTransactionAsRejected(orderData.clientOrderId(), reason);
            Transaction rejectedTransaction = portfolio.findTransactionByClientOrderId(orderData.clientOrderId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Transaction not found after reject update: " + orderData.clientOrderId()));
            portfolioRepository.saveTransaction(portfolio.getId(), rejectedTransaction);

            log.warn("Transaction marked as REJECTED - Portfolio: {}, Reason: {}",
                    portfolio.getName(), reason);

        } catch (Exception e) {
            log.error("Error handling order rejection - Portfolio: {}, ClientOrderId: {}, Error: {}",
                    portfolio.getName(), orderData.clientOrderId(), e.getMessage(), e);
        }
    }

    /**
     * Processa ordem expirada
     */
    private void handleExpiredOrder(Portfolio portfolio, OrderDataDto orderData) {
        log.warn("Processing EXPIRED order - Portfolio: {}, ClientOrderId: {}",
                portfolio.getName(), orderData.clientOrderId());

        try {
            // Cleanup: remover matches provisórios se for SELL
            cleanupMatchesForFailedSell(portfolio, orderData.clientOrderId());

            portfolio.updateTransactionStatus(orderData.clientOrderId(), TransactionStatus.EXPIRED);
            Transaction expiredTransaction = portfolio.findTransactionByClientOrderId(orderData.clientOrderId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Transaction not found after expiry update: " + orderData.clientOrderId()));
            portfolioRepository.saveTransaction(portfolio.getId(), expiredTransaction);

            log.warn("Transaction marked as EXPIRED - Portfolio: {}", portfolio.getName());

        } catch (Exception e) {
            log.error("Error handling order expiration - Portfolio: {}, ClientOrderId: {}, Error: {}",
                    portfolio.getName(), orderData.clientOrderId(), e.getMessage(), e);
        }
    }


    /**
     * Remove matches provisórios para um SELL que falhou (CANCELED/REJECTED/EXPIRED).
     * Libera as quantidades dos buys vinculados de volta para "free".
     */
    private void cleanupMatchesForFailedSell(Portfolio portfolio, String clientOrderId) {
        portfolio.findTransactionByClientOrderId(clientOrderId).ifPresent(tx -> {
            if (tx.isSell()) {
                portfolio.removeMatchesForSell(tx.id());
                portfolioRepository.deleteMatchesBySellTransactionId(tx.id());
                log.debug("Cleaned up provisional matches for failed SELL - Portfolio: {}, TxId: {}",
                        portfolio.getName(), tx.id());
            }
        });
    }

    @Override
    public boolean shouldProcess(OrderDataDto orderData) {
        return orderData.status() == OrderDataDto.OrderStatus.FILLED ||
                orderData.status() == OrderDataDto.OrderStatus.PARTIALLY_FILLED ||
                orderData.status() == OrderDataDto.OrderStatus.CANCELED ||
                orderData.status() == OrderDataDto.OrderStatus.EXPIRED;
    }
}