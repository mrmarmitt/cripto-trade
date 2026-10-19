package com.marmitt.core.application.usecase.runner.processsignal;

import com.marmitt.core.domain.Symbol;
import com.marmitt.core.dto.websocket.data.MarketDataDto;
import com.marmitt.core.ports.outbound.metrics.SignalMetricsPort;
import com.marmitt.core.ports.outbound.metrics.TickDiscardReason;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * T42: mailbox por runner — latest-wins, ordem, sequencial por runner e justica entre runners.
 */
class RunnerMailboxTest {

    private final SignalMetricsPort metrics = mock(SignalMetricsPort.class);
    private final UUID runnerA = UUID.randomUUID();
    private final UUID runnerB = UUID.randomUUID();
    private final Instant base = Instant.parse("2026-10-19T12:00:00Z");

    @Test
    void ticksWaitingForTheRunnerCoalesceIntoTheLatest() {
        ManualExecutor executor = new ManualExecutor();
        List<MarketDataDto> evaluated = new ArrayList<>();
        RunnerMailbox mailbox = new RunnerMailbox(executor, (runner, tick) -> evaluated.add(tick), metrics);

        mailbox.submit(runnerA, tick("100", 0));
        mailbox.submit(runnerA, tick("101", 1));
        mailbox.submit(runnerA, tick("102", 2));
        executor.runAll();

        assertEquals(1, evaluated.size(), "So o tick mais recente e avaliado");
        assertEquals(0, new BigDecimal("102").compareTo(evaluated.getFirst().price()));
        verify(metrics, times(2)).recordTickDiscarded(runnerA, TickDiscardReason.COALESCED);
    }

    @Test
    void olderTickThanTheLastAcceptedIsDiscarded() {
        ManualExecutor executor = new ManualExecutor();
        List<MarketDataDto> evaluated = new ArrayList<>();
        RunnerMailbox mailbox = new RunnerMailbox(executor, (runner, tick) -> evaluated.add(tick), metrics);

        mailbox.submit(runnerA, tick("101", 5));
        executor.runAll();
        mailbox.submit(runnerA, tick("100", 4));
        executor.runAll();

        assertEquals(1, evaluated.size());
        verify(metrics).recordTickDiscarded(runnerA, TickDiscardReason.OUT_OF_ORDER);
        verify(metrics, never()).recordTickDiscarded(runnerA, TickDiscardReason.COALESCED);
    }

    @Test
    void busyRunnerYieldsTheThreadToOtherRunners() {
        ManualExecutor executor = new ManualExecutor();
        List<UUID> order = new ArrayList<>();
        RunnerMailbox[] holder = new RunnerMailbox[1];
        AtomicInteger feed = new AtomicInteger(10);
        holder[0] = new RunnerMailbox(executor, (runner, tick) -> {
            order.add(runner);
            // Runner A recebe um tick novo a cada avaliacao (feed intenso).
            if (runner.equals(runnerA) && feed.getAndDecrement() > 0) {
                holder[0].submit(runnerA, tick("1", 100 - feed.get()));
            }
        }, metrics);

        holder[0].submit(runnerA, tick("1", 0));
        holder[0].submit(runnerB, tick("1", 0));
        executor.runOne();   // A
        executor.runOne();   // B, mesmo com A tendo tick novo pendente

        assertEquals(List.of(runnerA, runnerB), order);
    }

    @Test
    void evaluationsOfTheSameRunnerNeverOverlapAndRunOffTheSubmittingThread() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            AtomicInteger inFlight = new AtomicInteger();
            AtomicInteger maxInFlight = new AtomicInteger();
            List<Instant> seen = Collections.synchronizedList(new ArrayList<>());
            ConcurrentHashMap<String, Boolean> threads = new ConcurrentHashMap<>();
            CountDownLatch done = new CountDownLatch(1);
            RunnerMailbox mailbox = new RunnerMailbox(pool, (runner, tick) -> {
                maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
                threads.put(Thread.currentThread().getName(), true);
                sleep(2);
                seen.add(tick.timestamp());
                inFlight.decrementAndGet();
                if (tick.price().intValue() == 199) {
                    done.countDown();
                }
            }, metrics);

            for (int i = 0; i < 200; i++) {
                mailbox.submit(runnerA, tick(String.valueOf(i), i));
            }
            assertTrue(done.await(10, TimeUnit.SECONDS), "O ultimo tick precisa ser avaliado");

            assertEquals(1, maxInFlight.get(), "Um runner nunca e avaliado em paralelo");
            for (int i = 1; i < seen.size(); i++) {
                assertTrue(seen.get(i).isAfter(seen.get(i - 1)), "Ticks avaliados em ordem crescente");
            }
            assertNotEquals(Boolean.TRUE, threads.get(Thread.currentThread().getName()),
                    "A thread que entrega o tick nao avalia");
        } finally {
            pool.shutdownNow();
        }
    }

    private MarketDataDto tick(String price, int secondsAfterBase) {
        BigDecimal value = new BigDecimal(price);
        return new MarketDataDto("BINANCE", Symbol.of("BTCUSDT"), value, value, value, BigDecimal.ONE,
                value, value, BigDecimal.ZERO, BigDecimal.ZERO, base.plusSeconds(secondsAfterBase));
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Executor controlado pelo teste: as tarefas so rodam quando o teste manda. */
    private static final class ManualExecutor implements java.util.concurrent.Executor {
        private final Queue<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        void runOne() {
            tasks.remove().run();
        }

        void runAll() {
            while (!tasks.isEmpty()) {
                runOne();
            }
        }
    }
}
