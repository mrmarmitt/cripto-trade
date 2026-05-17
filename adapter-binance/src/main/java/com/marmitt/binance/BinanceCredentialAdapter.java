package com.marmitt.binance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.binance.auth.BinanceCredentials;
import com.marmitt.binance.userdata.ListenKeyManager;
import com.marmitt.core.ports.outbound.exchange.streaming.UserStreamCredentialPort;
import com.marmitt.core.ports.outbound.http.HttpClientPort;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
public class BinanceCredentialAdapter implements UserStreamCredentialPort {

    private static final long KEEPALIVE_INTERVAL_MINUTES = 30;

    private final ListenKeyManager listenKeyManager;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "binance-listenkey-keepalive");
        t.setDaemon(true);
        return t;
    });

    private ScheduledFuture<?> keepAliveTask;

    public BinanceCredentialAdapter(HttpClientPort httpClient,
                                    ObjectMapper objectMapper,
                                    BinanceConnectionConfig config) {
        var credentials = new BinanceCredentials(config.apiKey(), config.apiSecret());
        this.listenKeyManager = new ListenKeyManager(config.restBaseUrl(), credentials, httpClient, objectMapper);
    }

    @Override
    public String getExchangeName() {
        return "BINANCE";
    }

    @Override
    public String obtain(UUID connectionId) throws IOException {
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

        log.info("Binance listen key obtained and keepalive scheduled - connectionId={}", connectionId);
        return listenKey;
    }

    @Override
    public void revoke(UUID connectionId) {
        if (keepAliveTask != null) {
            keepAliveTask.cancel(false);
        }
        listenKeyManager.revoke();
        log.info("Binance listen key revoked - connectionId={}", connectionId);
    }
}
