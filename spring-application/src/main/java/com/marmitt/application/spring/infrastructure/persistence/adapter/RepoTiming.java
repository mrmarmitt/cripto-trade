package com.marmitt.application.spring.infrastructure.persistence.adapter;

import java.util.concurrent.TimeUnit;

final class RepoTiming {

    private RepoTiming() {
    }

    static long elapsedMs(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }
}
