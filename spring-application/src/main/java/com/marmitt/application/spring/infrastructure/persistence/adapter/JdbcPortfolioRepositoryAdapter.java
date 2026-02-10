package com.marmitt.application.spring.infrastructure.persistence.adapter;

import com.marmitt.application.spring.infrastructure.persistence.entity.BalanceEntity;
import com.marmitt.application.spring.infrastructure.persistence.entity.PortfolioEntity;
import com.marmitt.application.spring.infrastructure.persistence.entity.PositionEntity;
import com.marmitt.application.spring.infrastructure.persistence.entity.TransactionEntity;
import com.marmitt.application.spring.infrastructure.persistence.entity.TransactionMatchEntity;
import com.marmitt.application.spring.infrastructure.persistence.mapper.PortfolioEntityMapper;
import com.marmitt.application.spring.infrastructure.persistence.repository.BalanceJdbcRepository;
import com.marmitt.application.spring.infrastructure.persistence.repository.PortfolioJdbcRepository;
import com.marmitt.application.spring.infrastructure.persistence.repository.PositionJdbcRepository;
import com.marmitt.application.spring.infrastructure.persistence.repository.TransactionJdbcRepository;
import com.marmitt.application.spring.infrastructure.persistence.repository.TransactionMatchJdbcRepository;
import com.marmitt.core.domain.portfolio.Asset;
import com.marmitt.core.domain.portfolio.Balance;
import com.marmitt.core.domain.portfolio.Portfolio;
import com.marmitt.core.domain.portfolio.Position;
import com.marmitt.core.domain.portfolio.Transaction;
import com.marmitt.core.domain.portfolio.TransactionMatch;
import com.marmitt.core.ports.outbound.repository.PortfolioRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.StreamSupport;

@Slf4j
@Repository
@RequiredArgsConstructor
public class JdbcPortfolioRepositoryAdapter implements PortfolioRepositoryPort {

    private final PortfolioJdbcRepository portfolioRepository;
    private final BalanceJdbcRepository balanceRepository;
    private final PositionJdbcRepository positionRepository;
    private final TransactionJdbcRepository transactionRepository;
    private final TransactionMatchJdbcRepository transactionMatchRepository;

    @Override
    public Optional<Portfolio> findById(UUID portfolioId) {
        long start = System.nanoTime();
        Optional<Portfolio> result = portfolioRepository.findById(portfolioId)
                .map(entity -> assembleDomain(entity, portfolioId));
        log.trace("[REPO] findById({}) - {}ms", portfolioId, elapsedMs(start));
        return result;
    }

    @Override
    public Optional<Portfolio> findByName(String portfolioName) {
        long start = System.nanoTime();
        Optional<Portfolio> result = portfolioRepository.findByNameIgnoreCase(portfolioName)
                .map(entity -> assembleDomain(entity, entity.getId()));
        log.trace("[REPO] findByName({}) - {}ms", portfolioName, elapsedMs(start));
        return result;
    }

    @Override
    public List<Portfolio> findBySymbol(String symbol) {
        long start = System.nanoTime();
        List<Portfolio> result = portfolioRepository.findBySymbolIgnoreCase(symbol).stream()
                .map(entity -> assembleDomain(entity, entity.getId()))
                .toList();
        log.trace("[REPO] findBySymbol({}) - {}ms - {} results", symbol, elapsedMs(start), result.size());
        return result;
    }

    @Override
    public Optional<Portfolio> findBySymbolAndStrategy(String symbol, UUID strategyId) {
        long start = System.nanoTime();
        Optional<Portfolio> result = portfolioRepository.findBySymbolAndStrategyId(symbol, strategyId)
                .map(entity -> assembleDomain(entity, entity.getId()));
        log.trace("[REPO] findBySymbolAndStrategy({}, {}) - {}ms", symbol, strategyId, elapsedMs(start));
        return result;
    }

    @Override
    public List<Portfolio> findAll() {
        long start = System.nanoTime();
        List<Portfolio> result = StreamSupport.stream(portfolioRepository.findAll().spliterator(), false)
                .map(entity -> assembleDomain(entity, entity.getId()))
                .toList();
        log.trace("[REPO] findAll() - {}ms - {} results", elapsedMs(start), result.size());
        return result;
    }

    @Override
    @Transactional
    public void registerPortfolio(Portfolio portfolio) {
        long start = System.nanoTime();
        UUID portfolioId = portfolio.getId();

        if (portfolioRepository.existsById(portfolioId)) {
            log.warn("Portfolio already exists with ID: {}", portfolioId);
            return;
        }

        Optional<PortfolioEntity> existingByName = portfolioRepository.findByNameIgnoreCase(portfolio.getName());
        if (existingByName.isPresent() && !existingByName.get().getId().equals(portfolioId)) {
            throw new IllegalArgumentException(
                    "Portfolio with name '" + portfolio.getName() + "' already exists with different ID"
            );
        }

        // Save portfolio config
        PortfolioEntity entity = PortfolioEntityMapper.toPortfolioEntity(portfolio);
        portfolioRepository.save(entity);

        // Save initial balance
        BalanceEntity balanceEntity = PortfolioEntityMapper.toBalanceEntity(
                portfolioId, portfolio.getBalance(), portfolio.getLastExecutionTime()
        );
        balanceRepository.save(balanceEntity);

        // Na criação não há posição (sem transactions ainda), bloco mantido para robustez
        if (portfolio.hasPosition()) {
            Asset defaultPrice = Asset.of(java.math.BigDecimal.ZERO, portfolio.getSymbol().getQuoteAsset());
            PositionEntity positionEntity = PortfolioEntityMapper.toPositionEntity(portfolioId, portfolio.getPosition(defaultPrice), null);
            positionRepository.save(positionEntity);
        }

        log.info("Portfolio registered - ID: {}, Name: {}, Symbol: {}, Strategy: {}",
                portfolioId, portfolio.getName(), portfolio.getSymbol().value(), portfolio.getStrategyName());
        log.trace("[REPO] registerPortfolio({}) - {}ms", portfolioId, elapsedMs(start));
    }

    @Override
    public void saveBalance(UUID portfolioId, Balance balance, Instant lastExecutionTime) {
        long start = System.nanoTime();

        BalanceEntity balanceEntity = PortfolioEntityMapper.toBalanceEntity(portfolioId, balance, lastExecutionTime);
        balanceRepository.findById(portfolioId)
                .ifPresent(existing -> balanceEntity.setVersion(existing.getVersion()));
        balanceRepository.save(balanceEntity);

        log.debug("Balance saved - PortfolioId: {}", portfolioId);
        log.trace("[REPO] saveBalance({}) - {}ms", portfolioId, elapsedMs(start));
    }

    @Override
    public void saveTransaction(UUID portfolioId, Transaction transaction) {
        long start = System.nanoTime();

        TransactionEntity txEntity = PortfolioEntityMapper.toTransactionEntity(portfolioId, transaction);
        transactionRepository.findVersionById(transaction.id())
                .ifPresent(txEntity::setVersion);
        transactionRepository.save(txEntity);

        log.debug("Transaction saved - PortfolioId: {}, TxId: {}, Status: {}",
                portfolioId, transaction.id(), transaction.status());
        log.trace("[REPO] saveTransaction({}, {}) - {}ms", portfolioId, transaction.id(), elapsedMs(start));
    }

    @Override
    @Transactional
    public void saveTransactionWithMatches(UUID portfolioId, Transaction transaction, List<TransactionMatch> newMatches) {
        long start = System.nanoTime();

        TransactionEntity txEntity = PortfolioEntityMapper.toTransactionEntity(portfolioId, transaction);
        transactionRepository.findVersionById(transaction.id())
                .ifPresent(txEntity::setVersion);
        transactionRepository.save(txEntity);

        if (newMatches != null && !newMatches.isEmpty()) {
            List<TransactionMatchEntity> matchEntities = newMatches.stream()
                    .map(PortfolioEntityMapper::toMatchEntity)
                    .toList();
            transactionMatchRepository.saveAll(matchEntities);
            log.debug("Transaction with matches saved - PortfolioId: {}, TxId: {}, Matches: {}",
                    portfolioId, transaction.id(), newMatches.size());
        }

        log.trace("[REPO] saveTransactionWithMatches({}, {}) - {}ms", portfolioId, transaction.id(), elapsedMs(start));
    }

    @Override
    public void deleteMatchesBySellTransactionId(UUID sellTransactionId) {
        long start = System.nanoTime();
        transactionMatchRepository.deleteBySellTransactionId(sellTransactionId);
        log.debug("Matches deleted for sell transaction: {}", sellTransactionId);
        log.trace("[REPO] deleteMatchesBySellTransactionId({}) - {}ms", sellTransactionId, elapsedMs(start));
    }

    @Override
    @Transactional
    public void saveTradeExecution(UUID portfolioId, Balance balance, Instant lastExecutionTime,
                                   Position position, Transaction transaction,
                                   List<TransactionMatch> newMatches) {
        long start = System.nanoTime();

        // Save balance
        BalanceEntity balanceEntity = PortfolioEntityMapper.toBalanceEntity(portfolioId, balance, lastExecutionTime);
        balanceRepository.findById(portfolioId)
                .ifPresent(existing -> balanceEntity.setVersion(existing.getVersion()));
        balanceRepository.save(balanceEntity);

        // Save or delete position
        Optional<PositionEntity> existingPosition = positionRepository.findById(portfolioId);
        if (position != null && !position.isEmpty()) {
            PositionEntity positionEntity = PortfolioEntityMapper.toPositionEntity(
                    portfolioId, position,
                    existingPosition.map(PositionEntity::getOpenedAt).orElse(null)
            );
            existingPosition.ifPresent(existing -> positionEntity.setVersion(existing.getVersion()));
            positionRepository.save(positionEntity);
        } else if (existingPosition.isPresent()) {
            positionRepository.deleteById(portfolioId);
        }

        // Save transaction
        TransactionEntity txEntity = PortfolioEntityMapper.toTransactionEntity(portfolioId, transaction);
        transactionRepository.findVersionById(transaction.id())
                .ifPresent(txEntity::setVersion);
        transactionRepository.save(txEntity);

        // Save transaction matches
        if (newMatches != null && !newMatches.isEmpty()) {
            List<TransactionMatchEntity> matchEntities = newMatches.stream()
                    .map(PortfolioEntityMapper::toMatchEntity)
                    .toList();
            transactionMatchRepository.saveAll(matchEntities);
            log.debug("Transaction matches saved - PortfolioId: {}, Count: {}", portfolioId, newMatches.size());
        }

        log.debug("Trade execution saved - PortfolioId: {}, TxId: {}, Status: {}",
                portfolioId, transaction.id(), transaction.status());
        log.trace("[REPO] saveTradeExecution({}, {}) - {}ms", portfolioId, transaction.id(), elapsedMs(start));
    }

    private Portfolio assembleDomain(PortfolioEntity entity, UUID portfolioId) {
        BalanceEntity balanceEntity = balanceRepository.findById(portfolioId).orElse(null);
        PositionEntity positionEntity = positionRepository.findById(portfolioId).orElse(null);
        List<TransactionEntity> transactionEntities = transactionRepository.findByPortfolioId(portfolioId);
        List<TransactionMatchEntity> matchEntities = transactionMatchRepository.findByPortfolioId(portfolioId);

        return PortfolioEntityMapper.toDomain(entity, balanceEntity, positionEntity, transactionEntities, matchEntities);
    }

    private static double elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000.0;
    }
}
