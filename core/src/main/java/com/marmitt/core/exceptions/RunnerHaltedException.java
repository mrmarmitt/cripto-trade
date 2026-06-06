package com.marmitt.core.exceptions;

import java.util.UUID;

public class RunnerHaltedException extends RuntimeException {

    public RunnerHaltedException(UUID runnerId) {
        super("Signal discarded — runner is halted: " + runnerId);
    }
}
