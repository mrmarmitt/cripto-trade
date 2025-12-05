package com.marmitt.core.dto.websocket.response;

import java.time.Instant;
import java.util.Optional;

/**
 * Response padronizado para operações WebSocket.
 * 
 * Encapsula resultado de operações WebSocket com informações de sucesso/fracasso,
 * mensagens de erro, timestamps e dados adicionais quando necessário.
 */
public record WebSocketResponse(
    boolean success,
    String message,
    String errorCode,
    Instant timestamp,
    Object data
) {
    
    /**
     * Cria response de sucesso
     * 
     * @param message Mensagem de sucesso
     * @return Response de sucesso
     */
    public static WebSocketResponse success(String message) {
        return new WebSocketResponse(true, message, null, Instant.now(), null);
    }
    
    /**
     * Cria response de sucesso com dados
     * 
     * @param message Mensagem de sucesso
     * @param data Dados adicionais
     * @return Response de sucesso com dados
     */
    public static WebSocketResponse success(String message, Object data) {
        return new WebSocketResponse(true, message, null, Instant.now(), data);
    }
    
    /**
     * Cria response de falha
     * 
     * @param message Mensagem de erro
     * @return Response de falha
     */
    public static WebSocketResponse failure(String message) {
        return new WebSocketResponse(false, message, null, Instant.now(), null);
    }
    
    /**
     * Cria response de falha com código de erro
     * 
     * @param message Mensagem de erro
     * @param errorCode Código específico do erro
     * @return Response de falha com código
     */
    public static WebSocketResponse failure(String message, String errorCode) {
        return new WebSocketResponse(false, message, errorCode, Instant.now(), null);
    }
    
    /**
     * Cria response de falha a partir de exception
     * 
     * @param exception Exception ocorrida
     * @return Response de falha
     */
    public static WebSocketResponse fromException(Exception exception) {
        return new WebSocketResponse(
            false, 
            exception.getMessage(), 
            exception.getClass().getSimpleName(),
            Instant.now(), 
            null
        );
    }
    
    /**
     * Verifica se operação foi bem-sucedida
     * 
     * @return true se sucesso, false se falha
     */
    public boolean isSuccess() {
        return success;
    }
    
    /**
     * Verifica se operação falhou
     * 
     * @return true se falha, false se sucesso
     */
    public boolean isFailure() {
        return !success;
    }
    
    /**
     * Obtém código de erro se disponível
     * 
     * @return Optional com código de erro
     */
    public Optional<String> getErrorCode() {
        return Optional.ofNullable(errorCode);
    }
    
    /**
     * Obtém dados adicionais se disponíveis
     * 
     * @return Optional com dados
     */
    public Optional<Object> getData() {
        return Optional.ofNullable(data);
    }
}