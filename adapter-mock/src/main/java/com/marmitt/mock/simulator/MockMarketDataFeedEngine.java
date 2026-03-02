package com.marmitt.mock.simulator;

import com.marmitt.core.domain.Symbol;
import com.marmitt.core.dto.common.CurrencyPair;
import com.marmitt.core.dto.websocket.data.MarketDataDto;
import com.marmitt.core.ports.outbound.events.EventPublisherPort;
import com.marmitt.mock.config.MockMarketDataFeedConfig;
import com.marmitt.mock.processor.MockRawMessagePublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Generates synthetic market-data ticks and injects them into the raw-message pipeline.
 */
@Slf4j
public class MockMarketDataFeedEngine {

    private static final BigDecimal TEN_THOUSAND = new BigDecimal("10000");
    private static final String EXCHANGE_NAME = "MOCK";

    private final MockMarketDataFeedConfig config;
    private final MockRawMessagePublisher rawPublisher;
    private final long seed;
    private final ScheduledExecutorService scheduler;
    private final Map<String, FeedTask> tasksBySymbol = new ConcurrentHashMap<>();

    public MockMarketDataFeedEngine(EventPublisherPort eventPublisher,
                                    ObjectMapper objectMapper,
                                    MockMarketDataFeedConfig config,
                                    long seed) {
        this.config = config;
        this.seed = seed;
        this.rawPublisher = new MockRawMessagePublisher(eventPublisher, objectMapper, EXCHANGE_NAME, UUID.randomUUID());
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "mock-feed");
            t.setDaemon(true);
            return t;
        });
    }

    public void subscribe(List<CurrencyPair> pairs) {
        if (!config.enabled() || pairs == null || pairs.isEmpty()) {
            return;
        }
        for (CurrencyPair pair : pairs) {
            if (pair == null) {
                continue;
            }
            String symbol = normalizeSymbol(pair);
            tasksBySymbol.computeIfAbsent(symbol, this::startTask);
        }
    }

    public void unsubscribe(List<CurrencyPair> pairs) {
        if (pairs == null || pairs.isEmpty()) {
            return;
        }
        for (CurrencyPair pair : pairs) {
            if (pair == null) {
                continue;
            }
            String symbol = normalizeSymbol(pair);
            FeedTask task = tasksBySymbol.remove(symbol);
            if (task != null) {
                task.stop();
                log.info("Mock feed stopped for symbol={}", symbol);
            }
        }
    }

    public void shutdown() {
        reset();
        scheduler.shutdownNow();
    }

    public void reset() {
        tasksBySymbol.values().forEach(FeedTask::stop);
        tasksBySymbol.clear();
    }

    private FeedTask startTask(String symbol) {
        FeedTask task = new FeedTask(symbol, initialPriceFor(symbol));
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                task::tick,
                0L,
                config.tickIntervalMs(),
                TimeUnit.MILLISECONDS
        );
        task.attachFuture(future);
        log.info("Mock feed started for symbol={} intervalMs={}", symbol, config.tickIntervalMs());
        return task;
    }

    private BigDecimal initialPriceFor(String symbol) {
        int hash = Math.abs(symbol.hashCode() % 5000);
        BigDecimal offset = BigDecimal.valueOf(hash).divide(TEN_THOUSAND, 8, RoundingMode.HALF_UP);
        return config.defaultStartPrice()
                .multiply(BigDecimal.ONE.add(offset))
                .setScale(8, RoundingMode.HALF_UP);
    }

    private String normalizeSymbol(CurrencyPair pair) {
        String base = pair.baseCurrency() == null ? "" : pair.baseCurrency().trim().toUpperCase(Locale.ROOT);
        String quote = pair.quoteCurrency() == null ? "" : pair.quoteCurrency().trim().toUpperCase(Locale.ROOT);
        return base + quote;
    }

    private final class FeedTask {
        private final String symbol;
        private final Random random;
        private BigDecimal open24h;
        private BigDecimal lastPrice;
        private BigDecimal high24h;
        private BigDecimal low24h;
        private ScheduledFuture<?> future;

        private FeedTask(String symbol, BigDecimal startPrice) {
            this.symbol = symbol;
            this.random = new Random(seed + symbol.hashCode());
            this.open24h = startPrice;
            this.lastPrice = startPrice;
            this.high24h = startPrice;
            this.low24h = startPrice;
        }

        private void attachFuture(ScheduledFuture<?> future) {
            this.future = future;
        }

        private void stop() {
            if (future != null) {
                future.cancel(false);
            }
        }

        private void tick() {
            try {
                lastPrice = nextPrice(lastPrice, random);
                if (lastPrice.compareTo(high24h) > 0) {
                    high24h = lastPrice;
                }
                if (lastPrice.compareTo(low24h) < 0) {
                    low24h = lastPrice;
                }

                BigDecimal halfSpread = lastPrice
                        .multiply(BigDecimal.valueOf(config.spreadBps()))
                        .divide(TEN_THOUSAND, 8, RoundingMode.HALF_UP)
                        .divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP);
                BigDecimal bid = lastPrice.subtract(halfSpread).max(new BigDecimal("0.00000001"));
                BigDecimal ask = lastPrice.add(halfSpread);

                BigDecimal volumeFactor = BigDecimal.valueOf(0.5d + random.nextDouble());
                BigDecimal volume = config.defaultVolume()
                        .multiply(volumeFactor)
                        .setScale(8, RoundingMode.HALF_UP);

                BigDecimal change24h = lastPrice.subtract(open24h).setScale(8, RoundingMode.HALF_UP);
                BigDecimal changePercent24h = change24h
                        .divide(open24h, 8, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(3, RoundingMode.HALF_UP);

                MarketDataDto marketData = new MarketDataDto(
                        EXCHANGE_NAME,
                        Symbol.of(symbol),
                        lastPrice,
                        bid,
                        ask,
                        volume,
                        high24h,
                        low24h,
                        change24h,
                        changePercent24h,
                        Instant.now()
                );
                rawPublisher.publish(marketData);
            } catch (Exception e) {
                log.error("Mock feed tick failure symbol={} error={}", symbol, e.getMessage(), e);
            }
        }

        private BigDecimal nextPrice(BigDecimal current, Random random) {
            int maxDelta = config.maxDeltaBpsPerTick();
            if (maxDelta == 0) {
                return current;
            }
            int deltaBps = random.nextInt((maxDelta * 2) + 1) - maxDelta;
            BigDecimal factor = BigDecimal.ONE.add(
                    BigDecimal.valueOf(deltaBps).divide(TEN_THOUSAND, 8, RoundingMode.HALF_UP)
            );
            BigDecimal next = current.multiply(factor).setScale(8, RoundingMode.HALF_UP);
            if (next.compareTo(BigDecimal.ZERO) <= 0) {
                return current;
            }
            return next;
        }
    }
}
