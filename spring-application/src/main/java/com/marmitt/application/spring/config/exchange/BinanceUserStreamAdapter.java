package com.marmitt.application.spring.config.exchange;

import com.marmitt.binance.userdata.ListenKeyPort;
import com.marmitt.core.ports.outbound.exchange.adapter.ReceivedMessageProcessorPort;
import com.marmitt.core.ports.outbound.exchange.streaming.ExchangeUserStreamPort;
import com.marmitt.core.ports.outbound.websocket.WebSocketPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class BinanceUserStreamAdapter implements ExchangeUserStreamPort, SmartLifecycle {

    private static final long KEEPALIVE_INTERVAL_MINUTES = 30;

    private final WebSocketPort webSocketPort;
    private final ReceivedMessageProcessorPort receivedMessageProcessor;
    private final ListenKeyPort listenKeyPort;
    private final String wsBaseUrl;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            r -> new Thread(r, "binance-listenkey-keepalive"));

    private volatile boolean running = false;

    public BinanceUserStreamAdapter(WebSocketPort webSocketPort,
                                    ReceivedMessageProcessorPort receivedMessageProcessor,
                                    ListenKeyPort listenKeyPort,
                                    String wsBaseUrl) {
        this.webSocketPort = webSocketPort;
        this.receivedMessageProcessor = receivedMessageProcessor;
        this.listenKeyPort = listenKeyPort;
        this.wsBaseUrl = wsBaseUrl;
    }

    @Override
    public String getExchangeName() {
        return "BINANCE";
    }

    @Override
    public WebSocketPort getWebSocketPort() {
        return webSocketPort;
    }

    @Override
    public ReceivedMessageProcessorPort getReceivedMessageProcessor() {
        return receivedMessageProcessor;
    }

    @Override
    public void start() {
        try {
            String listenKey = listenKeyPort.obtainListenKey();
            String wsUrl = wsBaseUrl + "/ws/" + listenKey;
            UUID connectionId = UUID.randomUUID();
            webSocketPort.connect(wsUrl, "BINANCE", connectionId);

            scheduler.scheduleAtFixedRate(
                    listenKeyPort::keepAlive,
                    KEEPALIVE_INTERVAL_MINUTES,
                    KEEPALIVE_INTERVAL_MINUTES,
                    TimeUnit.MINUTES);

            running = true;
            log.info("Binance user data stream started");
        } catch (IOException e) {
            log.error("Failed to start Binance user data stream", e);
        }
    }

    @Override
    public void stop() {
        scheduler.shutdownNow();
        listenKeyPort.revoke();
        running = false;
        log.info("Binance user data stream stopped");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 100;
    }
}
