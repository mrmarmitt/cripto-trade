package com.marmitt.binance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.binance.auth.BinanceCredentials;
import com.marmitt.binance.processor.receive.BinanceUserDataProcessor;
import com.marmitt.binance.userdata.ListenKeyManager;
import com.marmitt.core.dto.processing.ProcessingResult;
import com.marmitt.core.dto.websocket.MessageContext;
import com.marmitt.core.dto.websocket.data.ProcessorResponse;
import com.marmitt.core.ports.outbound.exchange.streaming.ExchangeUserStreamPort;
import com.marmitt.core.ports.outbound.http.HttpClientPort;
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

    private final BinanceUserDataProcessor receivedMessageProcessor;
    private final ListenKeyManager listenKeyManager;
    private final String wsBaseUrl;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "binance-listenkey-keepalive");
        t.setDaemon(true);
        return t;
    });

    private ScheduledFuture<?> keepAliveTask;

    public BinanceUserStreamAdapter(HttpClientPort httpClient,
                                    ObjectMapper objectMapper,
                                    BinanceConnectionConfig config) {
        var credentials = new BinanceCredentials(config.apiKey(), config.apiSecret());
        this.receivedMessageProcessor = new BinanceUserDataProcessor(objectMapper);
        this.listenKeyManager         = new ListenKeyManager(config.restBaseUrl(), credentials, httpClient, objectMapper);
        this.wsBaseUrl                = config.wsBaseUrl();
    }

    @Override
    public String getExchangeName() {
        return "BINANCE";
    }

    @Override
    public String prepareConnection(UUID connectionId) throws IOException {
        if (keepAliveTask != null && !keepAliveTask.isDone()) {
            keepAliveTask.cancel(false);
        }
        if (listenKeyManager.getListenKey() != null) {
            listenKeyManager.revoke();
        }

        String listenKey = listenKeyManager.obtainListenKey();

        keepAliveTask = scheduler.scheduleAtFixedRate(
                listenKeyManager::keepAlive,
                KEEPALIVE_INTERVAL_MINUTES,
                KEEPALIVE_INTERVAL_MINUTES,
                TimeUnit.MINUTES);

        log.info("Binance user data stream prepared - connectionId={}", connectionId);
        return wsBaseUrl + "/ws/" + listenKey;
    }

    @Override
    public void onDisconnect(UUID connectionId) {
        if (keepAliveTask != null) {
            keepAliveTask.cancel(false);
        }
        listenKeyManager.revoke();
        log.info("Binance user data stream resources released - connectionId={}", connectionId);
    }

    @Override
    public ProcessingResult<? extends ProcessorResponse> processMessage(String rawMessage, MessageContext context) {
        return receivedMessageProcessor.processMessage(rawMessage, context);
    }
}
