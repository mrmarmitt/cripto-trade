package com.marmitt.binance;

public class BinanceConfiguration {
    
    public static final String BASE_URL = "wss://stream.binance.com:9443";
    public static final String SINGLE_STREAM_PATH = "/ws";
    public static final String COMBINED_STREAM_PATH = "/stream";
    
    private BinanceConfiguration() {
    }
}