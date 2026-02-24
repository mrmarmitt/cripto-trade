package com.marmitt.application.spring.infrastructure.persistence.adapter;

import com.marmitt.application.spring.infrastructure.persistence.mapper.PortfolioEntityMapper;
import com.marmitt.application.spring.infrastructure.persistence.repository.PortfolioJdbcRepository;
import com.marmitt.core.domain.portfolio.Portfolio;
import com.marmitt.core.ports.outbound.repository.PortfolioRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.StreamSupport;

@Slf4j
@Repository
@RequiredArgsConstructor
public class JdbcPortfolioRepositoryAdapter implements PortfolioRepositoryPort {

    private final PortfolioJdbcRepository portfolioRepository;

    @Override
    public Optional<Portfolio> findById(UUID portfolioId) {
        long start = System.nanoTime();
        Optional<Portfolio> result = portfolioRepository.findById(portfolioId)
                .map(PortfolioEntityMapper::toDomain);
        log.trace("[REPO] findById({}) - {}ms", portfolioId, elapsedMs(start));
        return result;
    }

    @Override
    public Optional<Portfolio> findByName(String portfolioName) {
        long start = System.nanoTime();
        Optional<Portfolio> result = portfolioRepository.findByNameIgnoreCase(portfolioName)
                .map(PortfolioEntityMapper::toDomain);
        log.trace("[REPO] findByName({}) - {}ms", portfolioName, elapsedMs(start));
        return result;
    }

    @Override
    public List<Portfolio> findBySymbol(String symbol) {
        long start = System.nanoTime();
        List<Portfolio> result = portfolioRepository.findBySymbol(symbol).stream()
                .map(PortfolioEntityMapper::toDomain)
                .toList();
        log.trace("[REPO] findBySymbol({}) - {}ms - {} results", symbol, elapsedMs(start), result.size());
        return result;
    }

    @Override
    public List<Portfolio> findAll() {
        long start = System.nanoTime();
        List<Portfolio> result = StreamSupport.stream(portfolioRepository.findAll().spliterator(), false)
                .map(PortfolioEntityMapper::toDomain)
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

        Optional<Portfolio> existingByName = portfolioRepository.findByNameIgnoreCase(portfolio.getName())
                .map(PortfolioEntityMapper::toDomain);
        if (existingByName.isPresent() && !existingByName.get().getId().equals(portfolioId)) {
            throw new IllegalArgumentException(
                    "Portfolio with name '" + portfolio.getName() + "' already exists with different ID"
            );
        }

        portfolioRepository.save(PortfolioEntityMapper.toPortfolioEntity(portfolio));

        log.info("Portfolio registered - ID: {}, Name: {}", portfolioId, portfolio.getName());
        log.trace("[REPO] registerPortfolio({}) - {}ms", portfolioId, elapsedMs(start));
    }

    private static double elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000.0;
    }
}
