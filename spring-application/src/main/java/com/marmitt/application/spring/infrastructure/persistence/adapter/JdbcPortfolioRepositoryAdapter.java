package com.marmitt.application.spring.infrastructure.persistence.adapter;

import com.marmitt.application.spring.infrastructure.persistence.entity.BalanceEntity;
import com.marmitt.application.spring.infrastructure.persistence.entity.PortfolioEntity;
import com.marmitt.application.spring.infrastructure.persistence.entity.PositionEntity;
import com.marmitt.application.spring.infrastructure.persistence.entity.TransactionEntity;
import com.marmitt.application.spring.infrastructure.persistence.mapper.PortfolioEntityMapper;
import com.marmitt.application.spring.infrastructure.persistence.repository.BalanceJdbcRepository;
import com.marmitt.application.spring.infrastructure.persistence.repository.PortfolioJdbcRepository;
import com.marmitt.application.spring.infrastructure.persistence.repository.PositionJdbcRepository;
import com.marmitt.application.spring.infrastructure.persistence.repository.TransactionJdbcRepository;
import com.marmitt.core.domain.portfolio.Balance;
import com.marmitt.core.domain.portfolio.Portfolio;
import com.marmitt.core.domain.portfolio.Position;
import com.marmitt.core.domain.portfolio.Transaction;
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

    @Override
    public Optional<Portfolio> findById(UUID portfolioId) {
        return portfolioRepository.findById(portfolioId)
                .map(entity -> assembleDomain(entity, portfolioId));
    }

    @Override
    public Optional<Portfolio> findByName(String portfolioName) {
        return portfolioRepository.findByNameIgnoreCase(portfolioName)
                .map(entity -> assembleDomain(entity, entity.getId()));
    }

    @Override
    public List<Portfolio> findBySymbol(String symbol) {
        return portfolioRepository.findBySymbolIgnoreCase(symbol).stream()
                .map(entity -> assembleDomain(entity, entity.getId()))
                .toList();
    }

    @Override
    public Optional<Portfolio> findBySymbolAndStrategy(String symbol, UUID strategyId) {
        return portfolioRepository.findBySymbolAndStrategyId(symbol, strategyId)
                .map(entity -> assembleDomain(entity, entity.getId()));
    }

    @Override
    public List<Portfolio> findAll() {
        return StreamSupport.stream(portfolioRepository.findAll().spliterator(), false)
                .map(entity -> assembleDomain(entity, entity.getId()))
                .toList();
    }

    @Override
    @Transactional
    public void registerPortfolio(Portfolio portfolio) {
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

        // Save position if exists
        if (portfolio.hasPosition()) {
            PositionEntity positionEntity = PortfolioEntityMapper.toPositionEntity(portfolioId, portfolio.getPosition(), null);
            positionRepository.save(positionEntity);
        }

        log.info("Portfolio registered - ID: {}, Name: {}, Symbol: {}, Strategy: {}",
                portfolioId, portfolio.getName(), portfolio.getSymbol().value(), portfolio.getStrategyName());
    }

    @Override
    public void saveBalance(UUID portfolioId, Balance balance, Instant lastExecutionTime) {
        BalanceEntity balanceEntity = PortfolioEntityMapper.toBalanceEntity(portfolioId, balance, lastExecutionTime);
        balanceRepository.findById(portfolioId)
                .ifPresent(existing -> balanceEntity.setVersion(existing.getVersion()));
        balanceRepository.save(balanceEntity);

        log.debug("Balance saved - PortfolioId: {}", portfolioId);
    }

    @Override
    public void saveTransaction(UUID portfolioId, Transaction transaction) {
        TransactionEntity txEntity = PortfolioEntityMapper.toTransactionEntity(portfolioId, transaction);
        transactionRepository.findVersionById(transaction.id())
                .ifPresent(txEntity::setVersion);
        transactionRepository.save(txEntity);

        log.debug("Transaction saved - PortfolioId: {}, TxId: {}, Status: {}",
                portfolioId, transaction.id(), transaction.status());
    }

    @Override
    @Transactional
    public void saveTradeExecution(UUID portfolioId, Balance balance, Instant lastExecutionTime,
                                   Position position, Transaction transaction) {
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

        log.debug("Trade execution saved - PortfolioId: {}, TxId: {}, Status: {}",
                portfolioId, transaction.id(), transaction.status());
    }

    private Portfolio assembleDomain(PortfolioEntity entity, UUID portfolioId) {
        BalanceEntity balanceEntity = balanceRepository.findById(portfolioId).orElse(null);
        PositionEntity positionEntity = positionRepository.findById(portfolioId).orElse(null);
        List<TransactionEntity> transactionEntities = transactionRepository.findByPortfolioId(portfolioId);

        return PortfolioEntityMapper.toDomain(entity, balanceEntity, positionEntity, transactionEntities);
    }
}
