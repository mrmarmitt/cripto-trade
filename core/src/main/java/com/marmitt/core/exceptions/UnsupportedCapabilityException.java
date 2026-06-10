package com.marmitt.core.exceptions;

public class UnsupportedCapabilityException extends RuntimeException {

    public UnsupportedCapabilityException(String exchangeName, String capability) {
        super("Exchange '" + exchangeName + "' does not support capability: " + capability);
    }
}
