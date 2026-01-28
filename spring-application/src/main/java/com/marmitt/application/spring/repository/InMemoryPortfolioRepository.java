package com.marmitt.application.spring.repository;

import com.marmitt.core.domain.Symbol;
import com.marmitt.core.domain.portfolio.Asset;
import com.marmitt.core.domain.portfolio.Portfolio;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import com.marmitt.core.ports.outbound.repository.PortfolioRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRepositoryPort;
import com.marmitt.core.ports.outbound.strategy.TradingStrategy;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Repository
public class InMemoryPortfolioRepository implements PortfolioRepositoryPort {

    private final Map<UUID, Portfolio> portfolios = new ConcurrentHashMap<>();
    private final Map<String, UUID> portfoliosByName = new ConcurrentHashMap<>();

    private final StrategyRepositoryPort strategyRepository;
    private final ExchangeAdapterRepositoryPort exchangeAdapterRepository;

    public InMemoryPortfolioRepository(
            @Lazy StrategyRepositoryPort strategyRepository,
            @Lazy ExchangeAdapterRepositoryPort exchangeAdapterRepository
    ) {
        this.strategyRepository = strategyRepository;
        this.exchangeAdapterRepository = exchangeAdapterRepository;
    }

    @PostConstruct
    public void init() {
        log.info("Initializing InMemoryPortfolioRepository with sample portfolio...");

        // Buscar a estratégia SimpleMovingAverageStrategy
        Optional<TradingStrategy> strategyOpt = strategyRepository.findByName("SimpleMovingAverageStrategy");

        if (strategyOpt.isEmpty()) {
            log.warn("SimpleMovingAverageStrategy not found, skipping sample portfolio creation");
            return;
        }

        TradingStrategy strategy = strategyOpt.get();

        // Criar portfolio de exemplo
        UUID portfolioId = UUID.fromString("144d34e3-1a73-4f29-bd24-3871edface81");
        Portfolio samplePortfolio = new Portfolio(
                portfolioId,
                "Sample-BTC-Portfolio",
                strategy.getStrategyId(),
                strategy.getStrategyName(),
                Symbol.of("BTCUSDT"),
                Asset.of(new BigDecimal("10000.00"), "USDT"),
                "MOCK",                          // Order execution na MOCK
                Set.of("BINANCE")                // Market data apenas da BINANCE
        );

        // Registrar portfolio
        registerPortfolio(samplePortfolio);

        // Associar portfolio ao exchange adapter (MOCK)
        exchangeAdapterRepository.registerPortfolioByAdapter("MOCK", portfolioId);

        log.info("Sample portfolio created - ID: {}, Name: {}, Symbol: {}, Strategy: {}, OrderExecution: MOCK, MarketDataSources: [BINANCE]",
                portfolioId,
                samplePortfolio.getName(),
                samplePortfolio.getSymbol().value(),
                samplePortfolio.getStrategyName());
    }

    @Override
    public Optional<Portfolio> findById(UUID portfolioId) {
        Portfolio portfolio = portfolios.get(portfolioId);
        return Optional.ofNullable(portfolio);
    }

    @Override
    public Optional<Portfolio> findByName(String portfolioName) {
        UUID portfolioId = portfoliosByName.get(portfolioName.toLowerCase());
        if (portfolioId == null) {
            return Optional.empty();
        }
        return findById(portfolioId);
    }

    @Override
    public List<Portfolio> findBySymbol(String symbol) {
        String normalizedSymbol = symbol.toUpperCase();
        List<Portfolio> result = portfolios.values().stream()
                .filter(portfolio -> portfolio.getSymbol().value().equalsIgnoreCase(normalizedSymbol))
                .collect(Collectors.toList());

        return result;
    }

    @Override
    public Optional<Portfolio> findBySymbolAndStrategy(String symbol, UUID strategyId) {
        String normalizedSymbol = symbol.toUpperCase();
        return portfolios.values().stream()
                .filter(portfolio -> portfolio.getSymbol().value().equalsIgnoreCase(normalizedSymbol))
                .filter(portfolio -> portfolio.getStrategyId().equals(strategyId))
                .findFirst();
    }

    @Override
    public List<Portfolio> findAll() {
        return List.copyOf(portfolios.values());
    }

    @Override
    public void registerPortfolio(Portfolio portfolio) {
        Objects.requireNonNull(portfolio, "Portfolio cannot be null");
        Objects.requireNonNull(portfolio.getId(), "Portfolio ID cannot be null");
        Objects.requireNonNull(portfolio.getName(), "Portfolio name cannot be null");

        UUID portfolioId = portfolio.getId();
        String portfolioName = portfolio.getName().toLowerCase();

        // Validar se já existe portfolio com mesmo nome
        if (portfoliosByName.containsKey(portfolioName)) {
            UUID existingId = portfoliosByName.get(portfolioName);
            if (!existingId.equals(portfolioId)) {
                throw new IllegalArgumentException(
                    "Portfolio with name '" + portfolio.getName() + "' already exists with different ID"
                );
            }
        }

        portfolios.put(portfolioId, portfolio);
        portfoliosByName.put(portfolioName, portfolioId);

        log.info("Portfolio registered - ID: {}, Name: {}, Symbol: {}, Strategy: {}",
                portfolioId,
                portfolio.getName(),
                portfolio.getSymbol().value(),
                portfolio.getStrategyName());
    }

    @Override
    public Portfolio save(Portfolio portfolio) {
        Objects.requireNonNull(portfolio, "Portfolio cannot be null");
        Objects.requireNonNull(portfolio.getId(), "Portfolio ID cannot be null");

        UUID portfolioId = portfolio.getId();

        if (!portfolios.containsKey(portfolioId)) {
            registerPortfolio(portfolio);
        } else {
            portfolios.put(portfolioId, portfolio);
        }

        return portfolio;
    }
}
