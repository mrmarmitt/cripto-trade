package com.marmitt.application.spring.config.exchange;

import com.marmitt.binance.userdata.ListenKeyPort;
import com.marmitt.core.ports.outbound.exchange.adapter.ReceivedMessageProcessorPort;
import com.marmitt.core.ports.outbound.exchange.streaming.ExchangeUserStreamPort;
import com.marmitt.core.ports.outbound.websocket.WebSocketPort;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
public class BinanceUserStreamAdapter implements ExchangeUserStreamPort {

    private static final long KEEPALIVE_INTERVAL_MINUTES = 30;

    private final WebSocketPort webSocketPort;
    private final ReceivedMessageProcessorPort receivedMessageProcessor;
    private final ListenKeyPort listenKeyPort;
    private final String wsBaseUrl;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            r -> new Thread(r, "binance-listenkey-keepalive"));

    private ScheduledFuture<?> keepAliveTask;

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
    public void connect(UUID connectionId) throws IOException {
        String listenKey = listenKeyPort.obtainListenKey();
        String wsUrl = wsBaseUrl + "/ws/" + listenKey;
        webSocketPort.connect(wsUrl, "BINANCE", connectionId);

        keepAliveTask = scheduler.scheduleAtFixedRate(
                listenKeyPort::keepAlive,
                KEEPALIVE_INTERVAL_MINUTES,
                KEEPALIVE_INTERVAL_MINUTES,
                TimeUnit.MINUTES);

        log.info("Binance user data stream connected");
    }

    @Override
    public void disconnect(UUID connectionId) {
        if (keepAliveTask != null) {
            keepAliveTask.cancel(false);
        }
        listenKeyPort.revoke();
        webSocketPort.disconnect("BINANCE", connectionId);
        log.info("Binance user data stream disconnected");
    }
}
