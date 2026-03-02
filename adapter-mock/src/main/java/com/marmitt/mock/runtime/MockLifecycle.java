package com.marmitt.mock.runtime;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Controls lifecycle state for the mock exchange runtime.
 */
public class MockLifecycle {

    private final AtomicBoolean running;
    private final Runnable onStop;
    private final Runnable onReset;

    public MockLifecycle(boolean startRunning, Runnable onStop, Runnable onReset) {
        this.running = new AtomicBoolean(startRunning);
        this.onStop = Objects.requireNonNull(onStop, "onStop cannot be null");
        this.onReset = Objects.requireNonNull(onReset, "onReset cannot be null");
    }

    public void start() {
        running.set(true);
    }

    public void stop() {
        boolean wasRunning = running.getAndSet(false);
        if (wasRunning) {
            onStop.run();
        }
    }

    public void reset() {
        onReset.run();
        running.set(true);
    }

    public boolean isRunning() {
        return running.get();
    }
}
