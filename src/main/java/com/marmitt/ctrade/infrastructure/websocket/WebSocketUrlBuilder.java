package com.marmitt.ctrade.infrastructure.websocket;

import lombok.extern.slf4j.Slf4j;

/**
 * Builder para construir URLs de WebSocket com parâmetros de stream.
 * Abstrai a lógica comum de montagem de URLs para diferentes exchanges.
 */
@Slf4j
public class WebSocketUrlBuilder {
    
    /**
     * Constrói URL de WebSocket adicionando streams à URL base.
     * 
     * @param baseUrl URL base do WebSocket
     * @param streamList Lista de streams formatada (ex: "btcusdt@ticker/ethusdt@ticker")
     * @return URL final com streams adicionados
     */
    public static String buildStreamUrl(String baseUrl, String streamList) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Base URL cannot be null or empty");
        }
        
        if (streamList == null || streamList.trim().isEmpty()) {
            log.warn("No streams provided, using base URL: {}", baseUrl);
            return baseUrl;
        }
        
        String finalUrl = addStreamsToUrl(baseUrl, streamList);
        log.debug("Built WebSocket URL: {} + streams({}) -> {}", 
            baseUrl, streamList, finalUrl);
        return finalUrl;
    }
    
    /**
     * Constrói URL simples sem streams (para feeds que não precisam de parâmetros).
     */
    public static String buildSimpleUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Base URL cannot be null or empty");
        }
        
        log.debug("Using simple WebSocket URL: {}", baseUrl);
        return baseUrl;
    }
    
    /**
     * Adiciona streams à URL baseado no formato da URL.
     */
    private static String addStreamsToUrl(String baseUrl, String streamList) {
        // Se a URL base já contém parâmetros de stream, substitui
        if (baseUrl.contains("?streams=")) {
            String result = baseUrl.replaceAll("\\?streams=.*$", "?streams=" + streamList);
            log.debug("Replaced existing streams parameter: {} -> {}", baseUrl, result);
            return result;
        }
        
        // Se a URL contém "/stream", adiciona parâmetro streams
        if (baseUrl.contains("/stream")) {
            String result = baseUrl + "?streams=" + streamList;
            log.debug("Added streams parameter to stream URL: {} -> {}", baseUrl, result);
            return result;
        }
        
        // Para URLs como "!ticker@arr", já está formatada
        if (baseUrl.contains("@")) {
            log.debug("URL already contains stream format, using as-is: {}", baseUrl);
            return baseUrl;
        }
        
        // Fallback: adiciona como query parameter
        String separator = baseUrl.contains("?") ? "&" : "?";
        String result = baseUrl + separator + "streams=" + streamList;
        log.debug("Added streams as query parameter: {} -> {}", baseUrl, result);
        return result;
    }
    
    /**
     * Valida se a URL é uma URL WebSocket válida.
     */
    public static boolean isValidWebSocketUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        
        String lowerUrl = url.toLowerCase();
        return lowerUrl.startsWith("ws://") || lowerUrl.startsWith("wss://");
    }
    
    /**
     * Normaliza URL WebSocket garantindo protocolo correto.
     */
    public static String normalizeWebSocketUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("URL cannot be null or empty");
        }
        
        // Se já é WebSocket URL, retorna como está
        if (isValidWebSocketUrl(url)) {
            return url;
        }
        
        // Converte HTTP para WebSocket
        if (url.toLowerCase().startsWith("http://")) {
            return url.replaceFirst("^http://", "ws://");
        }
        
        if (url.toLowerCase().startsWith("https://")) {
            return url.replaceFirst("^https://", "wss://");
        }
        
        // Se não tem protocolo, assume wss://
        log.warn("URL without protocol, assuming wss://: {}", url);
        return "wss://" + url;
    }
}