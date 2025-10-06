package com.marmitt.binance.response;

public record ErrorResponse(
        String id,
        Integer status,
        Error error
) {
    public record Error(
            Integer code,
            String msg,
            String data
    ) {}
}