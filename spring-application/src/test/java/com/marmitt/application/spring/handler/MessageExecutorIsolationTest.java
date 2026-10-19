package com.marmitt.application.spring.handler;

import com.marmitt.application.spring.bootstrap.RunnerSignalProperties;
import com.marmitt.application.spring.config.AsyncConfig;
import com.marmitt.application.spring.event.RawMarketMessageReceivedEvent;
import com.marmitt.application.spring.event.RawUserDataMessageReceivedEvent;
import com.marmitt.core.dto.processing.ProcessingResult;
import com.marmitt.core.dto.websocket.MessageContext;
import com.marmitt.core.ports.inbound.handler.HandlerProcessMessagePort;
import com.marmitt.core.ports.inbound.handler.HandlerProcessUserMessagePort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T42: market data e user data em executores separados. Com o caminho de market data travado, um
 * fill continua sendo conciliado na hora — e nenhum dos dois roda na thread que publica o evento
 * (a do socket).
 */
@SpringJUnitConfig(MessageExecutorIsolationTest.TestConfig.class)
class MessageExecutorIsolationTest {

    private static final CountDownLatch MARKET_RELEASE = new CountDownLatch(1);
    private static final CountDownLatch MARKET_STARTED = new CountDownLatch(1);
    private static final CountDownLatch FILL_PROCESSED = new CountDownLatch(1);
    private static final AtomicReference<String> MARKET_THREAD = new AtomicReference<>();
    private static final AtomicReference<String> USER_THREAD = new AtomicReference<>();

    @Autowired
    private ApplicationEventPublisher publisher;

    @AfterEach
    void release() {
        MARKET_RELEASE.countDown();
    }

    @Test
    void fillIsNotDelayedByAStuckMarketDataPath() throws Exception {
        MessageContext market = MessageContext.create("BINANCE", UUID.randomUUID());
        publisher.publishEvent(new RawMarketMessageReceivedEvent(this, "{\"tick\":1}", market));
        assertTrue(MARKET_STARTED.await(5, TimeUnit.SECONDS), "Market data deve comecar a processar");
        for (int i = 0; i < 50; i++) {
            publisher.publishEvent(new RawMarketMessageReceivedEvent(this, "{\"tick\":" + i + "}", market));
        }

        publisher.publishEvent(new RawUserDataMessageReceivedEvent(this, "{\"fill\":1}",
                MessageContext.createUserData("BINANCE", UUID.randomUUID())));

        assertTrue(FILL_PROCESSED.await(2, TimeUnit.SECONDS),
                "O fill nao pode esperar o market data travado");
        String publisherThread = Thread.currentThread().getName();
        assertTrue(MARKET_THREAD.get().startsWith("ws-market-") && !MARKET_THREAD.get().equals(publisherThread));
        assertTrue(USER_THREAD.get().startsWith("ws-user-") && !USER_THREAD.get().equals(publisherThread));
    }

    @Configuration
    @Import({AsyncConfig.class, RunnerSignalProperties.class,
            ProcessMessageEventListener.class, ProcessUserMessageEventListener.class})
    static class TestConfig {

        @Bean
        HandlerProcessMessagePort stuckMarketHandler() {
            return (raw, context) -> {
                MARKET_THREAD.compareAndSet(null, Thread.currentThread().getName());
                MARKET_STARTED.countDown();
                try {
                    MARKET_RELEASE.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return ProcessingResult.ignored(context.correlationId().toString(), "test");
            };
        }

        @Bean
        HandlerProcessUserMessagePort userHandler() {
            return (raw, context) -> {
                USER_THREAD.set(Thread.currentThread().getName());
                FILL_PROCESSED.countDown();
                return ProcessingResult.ignored(context.correlationId().toString(), "test");
            };
        }
    }
}
