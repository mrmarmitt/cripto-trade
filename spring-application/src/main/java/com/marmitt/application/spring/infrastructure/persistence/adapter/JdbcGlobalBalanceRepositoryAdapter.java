package com.marmitt.application.spring.infrastructure.persistence.adapter;

import com.marmitt.application.spring.infrastructure.persistence.mapper.GlobalBalanceEntityMapper;
import com.marmitt.application.spring.infrastructure.persistence.repository.GlobalBalanceJdbcRepository;
import com.marmitt.core.domain.portfolio.GlobalBalance;
import com.marmitt.core.ports.outbound.repository.GlobalBalanceRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Repository
@RequiredArgsConstructor
public class JdbcGlobalBalanceRepositoryAdapter implements GlobalBalanceRepositoryPort {

    private final GlobalBalanceJdbcRepository balanceRepo;

    @Override
    public Optional<GlobalBalance> findByPortfolioId(UUID portfolioId) {
        long start = System.nanoTime();
        Optional<GlobalBalance> result = balanceRepo.findById(portfolioId)
                .map(GlobalBalanceEntityMapper::toDomain);
        log.trace("[REPO] globalBalance.findByPortfolioId({}) - {}ms", portfolioId, RepoTiming.elapsedMs(start));
        return result;
    }

    @Override
    @Transactional
    public void save(GlobalBalance balance) {
        long start = System.nanoTime();
        balanceRepo.save(GlobalBalanceEntityMapper.toEntity(balance));
        log.trace("[REPO] globalBalance.save({}) - {}ms", balance.getPortfolioId(), RepoTiming.elapsedMs(start));
    }

    @Override
    @Transactional
    public boolean reserveAtomic(UUID portfolioId, BigDecimal amount) {
        long start = System.nanoTime();
        int affected = balanceRepo.reserveAtomic(portfolioId, amount);
        boolean reserved = affected == 1;
        log.trace("[REPO] globalBalance.reserveAtomic({}, {}) - {}ms - reserved={}",
                portfolioId, amount, RepoTiming.elapsedMs(start), reserved);
        return reserved;
    }
}
