package com.marmitt.core.exceptions;

import java.util.UUID;

public class RunnerNotFoundException extends RuntimeException implements CodedError {

    public RunnerNotFoundException(UUID runnerId) {
        super("Runner not found: " + runnerId);
    }

    @Override
    public String code() {
        return "RUNNER_NOT_FOUND";
    }
}
