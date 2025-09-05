package com.marmitt.core.dto.configuration;

import java.util.List;
import java.util.Map;

public record StreamConfiguration(
        String streamType,
        List<String> symbols,
        Map<String, String> streamParameters
) {
    public static StreamConfiguration ticker(List<String> symbols) {
        return new StreamConfiguration("ticker", symbols, Map.of());
    }
    
    public static StreamConfiguration bookTicker(List<String> symbols) {
        return new StreamConfiguration("bookTicker", symbols, Map.of());
    }
    
    public static StreamConfiguration depth(List<String> symbols) {
        return new StreamConfiguration("depth", symbols, Map.of());
    }
    
    public static StreamConfiguration depthWithLevels(List<String> symbols, String levels) {
        return new StreamConfiguration("depth", symbols, Map.of("levels", levels));
    }
    
    public static StreamConfiguration trade(List<String> symbols) {
        return new StreamConfiguration("trade", symbols, Map.of());
    }
    
    public static StreamConfiguration custom(String streamType, List<String> symbols, Map<String, String> parameters) {
        return new StreamConfiguration(streamType, symbols, parameters);
    }
}