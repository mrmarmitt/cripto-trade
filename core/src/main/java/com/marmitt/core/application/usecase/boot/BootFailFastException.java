package com.marmitt.core.application.usecase.boot;

public class BootFailFastException extends RuntimeException {

    private final String phase;
    private final String code;

    public BootFailFastException(String phase, String code, String message) {
        super(message + " code=" + code);
        this.phase = phase;
        this.code = code;
    }

    public String phase() {
        return phase;
    }

    public String code() {
        return code;
    }
}
