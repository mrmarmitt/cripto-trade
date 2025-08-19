package com.marmitt.ctrade.infrastructure.websocket;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.jetbrains.annotations.NotNull;

/**
 * Listener WebSocket abstrato que fornece implementação genérica para eventos de conexão.
 * 
 * Centraliza toda a lógica comum de WebSocket listeners:
 * - Gerenciamento de eventos de conexão/desconexão
 * - Integração com serviços de gerenciamento
 * - Template methods para processamento específico de mensagens
 * 
 * Subclasses devem implementar apenas a lógica específica da exchange.
 */
@Slf4j
public abstract class AbstractWebSocketListener extends WebSocketListener {
    
    private final WebSocketConnectionHandler connectionHandler;
    
    private final Runnable scheduleReconnectionCallback;
    
    protected AbstractWebSocketListener(WebSocketConnectionHandler connectionHandler,
                                        Runnable scheduleReconnectionCallback) {
        this.connectionHandler = connectionHandler;
        this.scheduleReconnectionCallback = scheduleReconnectionCallback;
    }
    
    /**
     * Retorna o nome da exchange para logs.
     */
    protected abstract String getExchangeName();
    
    /**
     * Template method para processar mensagens específicas da exchange.
     * Subclasses devem implementar a lógica de parsing e processamento.
     */
    protected abstract void processMessage(@NotNull String messageText);
    
    @Override
    public final void onOpen(@NotNull WebSocket webSocket, @NotNull Response response) {
        connectionHandler.handleConnectionOpened(getExchangeName());
        onConnectionEstablished(webSocket, response);
    }
    
    @Override
    public final void onMessage(@NotNull WebSocket webSocket, @NotNull String text) {
        try {
            connectionHandler.handleMessageReceived();
            
            log.debug("Received message from {}: {}", getExchangeName(), 
                     text.substring(0, Math.min(100, text.length())));
            
            processMessage(text);
            
        } catch (Exception e) {
            connectionHandler.handleProcessingError(getExchangeName(), e);
        }
    }
    
    @Override
    public final void onClosing(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
        connectionHandler.handleConnectionClosing(getExchangeName(), code, reason);
        onConnectionClosing(webSocket, code, reason);
    }
    
    @Override
    public final void onClosed(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
        boolean reconnected = connectionHandler.handleConnectionClosed(
            getExchangeName(), 
            code, 
            reason, 
            scheduleReconnectionCallback
        );
        
        onConnectionClosed(webSocket, code, reason, reconnected);
    }
    
    @Override
    public final void onFailure(@NotNull WebSocket webSocket, @NotNull Throwable t, Response response) {
        connectionHandler.handleConnectionFailure(
            getExchangeName(),
            t,
            scheduleReconnectionCallback
        );
        
        onConnectionFailed(webSocket, t, response);
    }
    
    // ========== Template Methods for Subclasses ==========
    
    /**
     * Chamado quando a conexão é estabelecida com sucesso.
     * Subclasses podem sobrescrever para lógica específica.
     */
    protected void onConnectionEstablished(@NotNull WebSocket webSocket, @NotNull Response response) {
        // Default: no additional logic
    }
    
    /**
     * Chamado quando a conexão está sendo fechada.
     * Subclasses podem sobrescrever para lógica específica.
     */
    protected void onConnectionClosing(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
        // Default: no additional logic
    }
    
    /**
     * Chamado quando a conexão foi fechada.
     * Subclasses podem sobrescrever para lógica específica.
     */
    protected void onConnectionClosed(@NotNull WebSocket webSocket, int code, @NotNull String reason, boolean willReconnect) {
        // Default: no additional logic
    }
    
    /**
     * Chamado quando há falha na conexão.
     * Subclasses podem sobrescrever para lógica específica.
     */
    protected void onConnectionFailed(@NotNull WebSocket webSocket, @NotNull Throwable t, Response response) {
        // Default: no additional logic
    }
}