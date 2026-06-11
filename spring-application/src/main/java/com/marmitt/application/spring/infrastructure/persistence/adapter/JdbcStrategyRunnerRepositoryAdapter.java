package com.marmitt.application.spring.infrastructure.persistence.adapter;

import com.marmitt.application.spring.infrastructure.persistence.entity.RunnerPositionEntity;
import com.marmitt.application.spring.infrastructure.persistence.mapper.StrategyRunnerEntityMapper;
import com.marmitt.application.spring.infrastructure.persistence.repository.RunnerPositionJdbcRepository;
import com.marmitt.application.spring.infrastructure.persistence.repository.RunnerTransactionJdbcRepository;
import com.marmitt.application.spring.infrastructure.persistence.repository.RunnerTransactionMatchJdbcRepository;
import com.marmitt.application.spring.infrastructure.persistence.repository.StrategyRunnerJdbcRepository;
import com.marmitt.core.domain.runner.Position;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.domain.runner.TransactionMatch;
import com.marmitt.core.enums.PositionStatus;
import com.marmitt.core.enums.RunnerStatus;
import com.marmitt.core.enums.TransactionStatus;
import com.marmitt.core.exceptions.ConcurrentPositionLockException;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.relational.core.conversion.DbActionExecutionException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.util.Collection;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.StreamSupport;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Repository
@RequiredArgsConstructor
public class JdbcStrategyRunnerRepositoryAdapter implements StrategyRunnerRepositoryPort {

    private static final DefaultTransactionDefinition NESTED_TX_DEF =
            new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_NESTED);

    private final StrategyRunnerJdbcRepository runnerRepo;
    private final RunnerPositionJdbcRepository positionRepo;
    private final RunnerTransactionJdbcRepository transactionRepo;
    private final RunnerTransactionMatchJdbcRepository matchRepo;
    private final PlatformTransactionManager txManager;

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

    @Override
    public List<StrategyRunner> findAll() {
        long start = System.nanoTime();
        List<StrategyRunner> result = StreamSupport.stream(runnerRepo.findAll().spliterator(), false)
                .map(StrategyRunnerEntityMapper::toDomain)
                .toList();
        log.trace("[REPO] runner.findAll() - {}ms - {} results", RepoTiming.elapsedMs(start), result.size());
        return result;
    }

    @Override
    public List<StrategyRunner> findAllByStatus(RunnerStatus status) {
        long start = System.nanoTime();
        List<StrategyRunner> result = runnerRepo.findAllByStatus(status.name()).stream()
                .map(StrategyRunnerEntityMapper::toDomain)
                .toList();
        log.trace("[REPO] runner.findAllByStatus({}) - {}ms - {} results", status, RepoTiming.elapsedMs(start), result.size());
        return result;
    }

    // ── Position ────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void savePosition(Position position) {
        long start = System.nanoTime();
        validateOpenedByInvariant(position);
        positionRepo.save(StrategyRunnerEntityMapper.toEntity(position));
        log.trace("[REPO] position.save({}) - {}ms", position.getId(), RepoTiming.elapsedMs(start));
    }

    @Override
    public boolean trySavePosition(Position position) {
        long start = System.nanoTime();
        validateOpenedByInvariant(position);
        RunnerPositionEntity entity = StrategyRunnerEntityMapper.toEntity(position);
        // PROPAGATION_NESTED creates a savepoint inside the outer fill transaction.
        // On duplicate key the savepoint is rolled back (restoring the PostgreSQL connection)
        // and the outer transaction continues cleanly — preventing the orphaned position
        // that REQUIRES_NEW would leave committed if saveTransaction later rolls back.
        org.springframework.transaction.TransactionStatus savepoint = txManager.getTransaction(NESTED_TX_DEF);
        try {
            positionRepo.save(entity);
            txManager.commit(savepoint);
            log.trace("[REPO] position.trySave({}) - inserted - {}ms", position.getId(), RepoTiming.elapsedMs(start));
            return true;
        } catch (DataIntegrityViolationException | DbActionExecutionException e) {
            txManager.rollback(savepoint);
            if (e instanceof DataIntegrityViolationException ||
                    (e instanceof DbActionExecutionException && e.getCause() instanceof DataIntegrityViolationException)) {
                log.trace("[REPO] position.trySave({}) - duplicate open - {}ms", position.getId(), RepoTiming.elapsedMs(start));
                return false;
            }
            throw e;
        }
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
    public Optional<Position> findPositionByIdForUpdate(UUID positionId) {
        long start = System.nanoTime();
        log.trace("[REPO] position.findByIdForUpdate({}) - acquiring FOR UPDATE lock", positionId);
        Optional<Position> result = positionRepo.findByIdForUpdate(positionId)
                .map(StrategyRunnerEntityMapper::toDomain);
        log.trace("[REPO] position.findByIdForUpdate({}) - {}ms - {}",
                positionId, RepoTiming.elapsedMs(start), result.isPresent() ? "1 result" : "0 results");
        return result;
    }

    @Override
    public Optional<Position> findPositionByOpenedByTransactionId(UUID transactionId) {
        long start = System.nanoTime();
        Optional<Position> result = positionRepo.findByOpenedByTransactionId(transactionId)
                .map(StrategyRunnerEntityMapper::toDomain);
        log.trace("[REPO] position.findByOpenedByTransactionId({}) - {}ms", transactionId, RepoTiming.elapsedMs(start));
        return result;
    }

    @Override
    public Optional<Position> findPositionByOpenedByTransactionIdForUpdate(UUID transactionId) {
        long start = System.nanoTime();
        log.trace("[REPO] position.findByOpenedByTransactionIdForUpdate({}) - acquiring FOR UPDATE lock", transactionId);
        Optional<Position> result = positionRepo.findByOpenedByTransactionIdForUpdate(transactionId)
                .map(StrategyRunnerEntityMapper::toDomain);
        log.trace("[REPO] position.findByOpenedByTransactionIdForUpdate({}) - {}ms - {}",
                transactionId, RepoTiming.elapsedMs(start), result.isPresent() ? "1 result" : "0 results");
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

    @Override
    public Optional<Position> findOpenPositionByRunnerIdAndSymbolForUpdate(UUID runnerId, String symbol) {
        long start = System.nanoTime();
        log.trace("[REPO] position.findOpenByRunnerIdAndSymbolForUpdate({}, {}) - acquiring FOR UPDATE lock",
                runnerId, symbol);
        Optional<Position> result = positionRepo.findOpenByRunnerIdAndSymbolForUpdate(runnerId, symbol)
                .map(StrategyRunnerEntityMapper::toDomain);
        log.trace("[REPO] position.findOpenByRunnerIdAndSymbolForUpdate({}, {}) - {}ms - {}",
                runnerId, symbol, RepoTiming.elapsedMs(start), result.isPresent() ? "1 result" : "0 results");
        return result;
    }

    @Override
    public Optional<Position> findActivePositionByRunnerIdAndSymbol(UUID runnerId, String symbol) {
        long start = System.nanoTime();
        Optional<Position> result = positionRepo.findActiveByRunnerIdAndSymbol(runnerId, symbol)
                .map(StrategyRunnerEntityMapper::toDomain);
        log.trace("[REPO] position.findActiveByRunnerIdAndSymbol({}, {}) - {}ms", runnerId, symbol, RepoTiming.elapsedMs(start));
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

    @Override
    public List<Transaction> findByStatusesUpdatedBefore(Collection<TransactionStatus> statuses,
                                                         Instant updatedBefore,
                                                         int limit) {
        long start = System.nanoTime();
        List<String> statusNames = statuses.stream().map(Enum::name).toList();
        int boundedLimit = Math.max(0, limit);
        List<Transaction> result = transactionRepo.findByStatusesUpdatedBefore(
                        statusNames,
                        updatedBefore,
                        boundedLimit
                ).stream()
                .map(StrategyRunnerEntityMapper::toDomain)
                .toList();
        log.trace("[REPO] transaction.findByStatusesUpdatedBefore({}, {}, {}) - {}ms - {} results",
                statusNames, updatedBefore, boundedLimit, RepoTiming.elapsedMs(start), result.size());
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
        validateOpenedByInvariant(lockedPosition);
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

    @Override
    @Transactional
    public boolean tryLockPositionForSell(UUID positionId, UUID transactionId, BigDecimal quantity) {
        long start = System.nanoTime();
        BigDecimal normalizedQuantity = quantity.setScale(8, RoundingMode.HALF_UP);
        int updated = positionRepo.tryLockPositionForSell(positionId, transactionId, normalizedQuantity);
        boolean locked = updated == 1 || positionRepo.isLockedByTransaction(positionId, transactionId, normalizedQuantity);
        log.trace("[REPO] position.tryLockForSell(pos={}, tx={}, locked={}, updated={}) - {}ms",
                positionId, transactionId, locked, updated, RepoTiming.elapsedMs(start));
        return locked;
    }

    @Override
    public List<Transaction> findFilledBySymbolAndPeriod(String symbol, String exchangeId, Instant from, Instant to) {
        long start = System.nanoTime();
        List<Transaction> result = transactionRepo.findFilledBySymbolAndPeriod(symbol, exchangeId, from, to).stream()
                .map(StrategyRunnerEntityMapper::toDomain)
                .toList();
        log.trace("[REPO] transaction.findFilledBySymbolAndPeriod({}, {}, {}, {}) - {}ms - {} results",
                symbol, exchangeId, from, to, RepoTiming.elapsedMs(start), result.size());
        return result;
    }

    private static void validateOpenedByInvariant(Position position) {
        PositionStatus status = position.getStatus();
        boolean active = status == PositionStatus.OPEN || status == PositionStatus.CLOSING;
        if (active && position.getOpenedByTransactionId() == null) {
            throw new IllegalStateException("Position invariant violation: openedByTransactionId is required for status="
                    + status + " positionId=" + position.getId());
        }
    }
}
