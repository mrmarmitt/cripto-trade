package com.marmitt.binance.userdata;

import com.marmitt.core.ports.outbound.exchange.streaming.UserStreamSession;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
public class BinanceUserStreamSession implements UserStreamSession {

    private static final long KEEPALIVE_INTERVAL_MINUTES = 30;

    private final ListenKeyManager listenKeyManager;
    private final String wsBaseUrl;
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> keepAliveTask;

    public BinanceUserStreamSession(ListenKeyManager listenKeyManager, String wsBaseUrl) {
        this.listenKeyManager = listenKeyManager;
        this.wsBaseUrl = wsBaseUrl;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "binance-listenkey-keepalive");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public String open() throws IOException {
        String listenKey = listenKeyManager.obtainListenKey();
        keepAliveTask = scheduler.scheduleAtFixedRate(
                listenKeyManager::keepAlive,
                KEEPALIVE_INTERVAL_MINUTES,
                KEEPALIVE_INTERVAL_MINUTES,
                TimeUnit.MINUTES);
        log.info("Binance user stream session opened — keepalive scheduled every {}min", KEEPALIVE_INTERVAL_MINUTES);
        return wsBaseUrl + "/ws/" + listenKey;
    }

    @Override
    public void close() {
        if (keepAliveTask != null) {
            keepAliveTask.cancel(false);
        }
        scheduler.shutdown();
        listenKeyManager.revoke();
        log.info("Binance user stream session closed");
    }
}
