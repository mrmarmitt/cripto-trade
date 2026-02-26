package com.marmitt.application.spring.infrastructure.persistence.adapter;

import com.marmitt.application.spring.infrastructure.persistence.mapper.StrategyRunnerEntityMapper;
import com.marmitt.application.spring.infrastructure.persistence.repository.RunnerPositionJdbcRepository;
import com.marmitt.application.spring.infrastructure.persistence.repository.RunnerTransactionJdbcRepository;
import com.marmitt.application.spring.infrastructure.persistence.repository.RunnerTransactionMatchJdbcRepository;
import com.marmitt.application.spring.infrastructure.persistence.repository.StrategyRunnerJdbcRepository;
import com.marmitt.core.domain.runner.Position;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.domain.runner.TransactionMatch;
import com.marmitt.core.enums.TransactionStatus;
import com.marmitt.core.exceptions.ConcurrentPositionLockException;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Repository
@RequiredArgsConstructor
public class JdbcStrategyRunnerRepositoryAdapter implements StrategyRunnerRepositoryPort {

    private final StrategyRunnerJdbcRepository runnerRepo;
    private final RunnerPositionJdbcRepository positionRepo;
    private final RunnerTransactionJdbcRepository transactionRepo;
    private final RunnerTransactionMatchJdbcRepository matchRepo;

    // ── StrategyRunner ──────────────────────────────────────────────────────

    @Override
    @Transactional
    public void save(StrategyRunner runner) {
        long start = System.nanoTime();
        runnerRepo.save(StrategyRunnerEntityMapper.toEntity(runner));
        log.trace("[REPO] runner.save({}) - {}ms", runner.getId(), RepoTiming.elapsedMs(start));
    }

    @Override
    public Optional<StrategyRunner> findById(UUID runnerId) {
        long start = System.nanoTime();
        Optional<StrategyRunner> result = runnerRepo.findById(runnerId)
                .map(StrategyRunnerEntityMapper::toDomain);
        log.trace("[REPO] runner.findById({}) - {}ms", runnerId, RepoTiming.elapsedMs(start));
        return result;
    }

    @Override
    public List<StrategyRunner> findByPortfolioId(UUID portfolioId) {
        long start = System.nanoTime();
        List<StrategyRunner> result = runnerRepo.findByPortfolioId(portfolioId).stream()
                .map(StrategyRunnerEntityMapper::toDomain)
                .toList();
        log.trace("[REPO] runner.findByPortfolioId({}) - {}ms - {} results", portfolioId, RepoTiming.elapsedMs(start), result.size());
        return result;
    }

    @Override
    public List<StrategyRunner> findOperationalByPortfolioId(UUID portfolioId) {
        long start = System.nanoTime();
        List<StrategyRunner> result = runnerRepo.findOperationalByPortfolioId(portfolioId).stream()
                .map(StrategyRunnerEntityMapper::toDomain)
                .toList();
        log.trace("[REPO] runner.findOperationalByPortfolioId({}) - {}ms - {} results", portfolioId, RepoTiming.elapsedMs(start), result.size());
        return result;
    }

    @Override
    public List<StrategyRunner> findOperationalBySymbol(String symbol, String exchangeId) {
        long start = System.nanoTime();
        List<StrategyRunner> result = runnerRepo.findOperationalBySymbolAndExchange(symbol, exchangeId).stream()
                .map(StrategyRunnerEntityMapper::toDomain)
                .toList();
        log.trace("[REPO] runner.findOperationalBySymbol({}, {}) - {}ms - {} results", symbol, exchangeId, RepoTiming.elapsedMs(start), result.size());
        return result;
    }

    @Override
    public Optional<StrategyRunner> findByShortCodeAndPortfolioId(String shortCode, UUID portfolioId) {
        long start = System.nanoTime();
        Optional<StrategyRunner> result = runnerRepo.findByShortCodeAndPortfolioId(shortCode, portfolioId)
                .map(StrategyRunnerEntityMapper::toDomain);
        log.trace("[REPO] runner.findByShortCode({}, {}) - {}ms", shortCode, portfolioId, RepoTiming.elapsedMs(start));
        return result;
    }

    // ── Position ────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void savePosition(Position position) {
        long start = System.nanoTime();
        positionRepo.save(StrategyRunnerEntityMapper.toEntity(position));
        log.trace("[REPO] position.save({}) - {}ms", position.getId(), RepoTiming.elapsedMs(start));
    }

    @Override
    public Optional<Position> findPositionById(UUID positionId) {
        long start = System.nanoTime();
        Optional<Position> result = positionRepo.findById(positionId)
                .map(StrategyRunnerEntityMapper::toDomain);
        log.trace("[REPO] position.findById({}) - {}ms", positionId, RepoTiming.elapsedMs(start));
        return result;
    }

    @Override
    public List<Position> findOpenPositionsByRunnerId(UUID runnerId) {
        long start = System.nanoTime();
        List<Position> result = positionRepo.findOpenByRunnerId(runnerId).stream()
                .map(StrategyRunnerEntityMapper::toDomain)
                .toList();
        log.trace("[REPO] position.findOpenByRunnerId({}) - {}ms - {} results", runnerId, RepoTiming.elapsedMs(start), result.size());
        return result;
    }

    @Override
    public Optional<Position> findOpenPositionByRunnerIdAndSymbol(UUID runnerId, String symbol) {
        long start = System.nanoTime();
        Optional<Position> result = positionRepo.findOpenByRunnerIdAndSymbol(runnerId, symbol)
                .map(StrategyRunnerEntityMapper::toDomain);
        log.trace("[REPO] position.findOpenByRunnerIdAndSymbol({}, {}) - {}ms", runnerId, symbol, RepoTiming.elapsedMs(start));
        return result;
    }

    // ── Transaction ─────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void saveTransaction(Transaction transaction) {
        long start = System.nanoTime();
        transactionRepo.save(StrategyRunnerEntityMapper.toEntity(transaction));
        log.trace("[REPO] transaction.save({}) - {}ms", transaction.getId(), RepoTiming.elapsedMs(start));
    }

    @Override
    public Optional<Transaction> findTransactionById(UUID transactionId) {
        long start = System.nanoTime();
        Optional<Transaction> result = transactionRepo.findById(transactionId)
                .map(StrategyRunnerEntityMapper::toDomain);
        log.trace("[REPO] transaction.findById({}) - {}ms", transactionId, RepoTiming.elapsedMs(start));
        return result;
    }

    @Override
    public Optional<Transaction> findTransactionByClientOrderId(String clientOrderId) {
        long start = System.nanoTime();
        Optional<Transaction> result = transactionRepo.findByClientOrderId(clientOrderId)
                .map(StrategyRunnerEntityMapper::toDomain);
        log.trace("[REPO] transaction.findByClientOrderId({}) - {}ms", clientOrderId, RepoTiming.elapsedMs(start));
        return result;
    }

    @Override
    public List<Transaction> findByRunnerIdAndStatuses(UUID runnerId, Collection<TransactionStatus> statuses) {
        long start = System.nanoTime();
        List<String> statusNames = statuses.stream().map(Enum::name).toList();
        List<Transaction> result = transactionRepo.findByRunnerIdAndStatuses(runnerId, statusNames).stream()
                .map(StrategyRunnerEntityMapper::toDomain)
                .toList();
        log.trace("[REPO] transaction.findByRunnerIdAndStatuses({}, {}) - {}ms - {} results",
                runnerId, statusNames, RepoTiming.elapsedMs(start), result.size());
        return result;
    }

    // ── TransactionMatch ─────────────────────────────────────────────────────

    @Override
    @Transactional
    public void saveTransactionMatch(TransactionMatch match) {
        long start = System.nanoTime();
        matchRepo.save(StrategyRunnerEntityMapper.toEntity(match));
        log.trace("[REPO] match.save({}) - {}ms", match.id(), RepoTiming.elapsedMs(start));
    }

    @Override
    public boolean existsTransactionMatchById(UUID matchId) {
        return matchRepo.existsById(matchId);
    }

    @Override
    public List<TransactionMatch> findMatchesByTransactionId(UUID transactionId) {
        long start = System.nanoTime();
        List<TransactionMatch> result = matchRepo.findByTransactionId(transactionId).stream()
                .map(StrategyRunnerEntityMapper::toDomain)
                .toList();
        log.trace("[REPO] match.findByTransactionId({}) - {}ms - {} results", transactionId, RepoTiming.elapsedMs(start), result.size());
        return result;
    }

    // ── Atomic operations ───────────────────────────────────────────────────

    @Override
    @Transactional
    public void saveAtomicTransactionAndPositionLock(Transaction transaction, Position lockedPosition) {
        long start = System.nanoTime();
        try {
            transactionRepo.save(StrategyRunnerEntityMapper.toEntity(transaction));
            positionRepo.save(StrategyRunnerEntityMapper.toEntity(lockedPosition));
            log.trace("[REPO] saveAtomicTransactionAndPositionLock(tx={}, pos={}) - {}ms",
                    transaction.getId(), lockedPosition.getId(), RepoTiming.elapsedMs(start));
        } catch (OptimisticLockingFailureException e) {
            throw new ConcurrentPositionLockException(lockedPosition.getId(), transaction.getRunnerId());
        }
    }

    @Override
    @Transactional
    public void saveAtomicTransactionAndMatch(Transaction transaction, TransactionMatch match) {
        long start = System.nanoTime();
        transactionRepo.save(StrategyRunnerEntityMapper.toEntity(transaction));
        matchRepo.save(StrategyRunnerEntityMapper.toEntity(match));
        log.trace("[REPO] saveAtomicTransactionAndMatch(tx={}, match={}) - {}ms",
                transaction.getId(), match.id(), RepoTiming.elapsedMs(start));
    }
}
