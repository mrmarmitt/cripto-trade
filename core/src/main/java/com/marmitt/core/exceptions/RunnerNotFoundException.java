package com.marmitt.core.exceptions;

import java.util.UUID;

public class RunnerNotFoundException extends RuntimeException {

    public RunnerNotFoundException(UUID runnerId) {
        super("Runner not found: " + runnerId);
    }
}
