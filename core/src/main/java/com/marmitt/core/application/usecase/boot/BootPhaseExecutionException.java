package com.marmitt.core.application.usecase.boot;

public class BootPhaseExecutionException extends RuntimeException {

    private final String phase;

    public BootPhaseExecutionException(String phase, String message, Throwable cause) {
        super(message, cause);
        this.phase = phase;
    }

    public String phase() {
        return phase;
    }
}
