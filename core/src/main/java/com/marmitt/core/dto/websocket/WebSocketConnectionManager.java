package com.marmitt.core.dto.websocket;

import com.marmitt.core.domain.ConnectionResult;
import com.marmitt.core.enums.ConnectionStatus;

import java.util.concurrent.CompletableFuture;

public class WebSocketConnectionManager {

    private volatile ConnectionResult currentResult;
    private volatile CompletableFuture<ConnectionResult> pendingOperation;

    private WebSocketConnectionManager(ConnectionResult connectionResult) {
        this.currentResult = connectionResult;
        this.pendingOperation = CompletableFuture.completedFuture(connectionResult);
    }


    public static WebSocketConnectionManager forExchange(String exchangeName) {
        return new WebSocketConnectionManager(ConnectionResult.idle(exchangeName));
    }

    public CompletableFuture<ConnectionResult> startConnection() {
        ConnectionResult current = currentResult;
        
        return switch (current.status()) {
            case CONNECTED -> 
                CompletableFuture.completedFuture(
                    current.withMetadata("message", "Already connected")
                );
                
            case CONNECTING -> {
                if (pendingOperation != null && !pendingOperation.isDone()) {
                    yield pendingOperation; // Retorna operação em andamento
                }
                // Se não há operação pendente, inicia nova
                currentResult = current.connecting();
                pendingOperation = new CompletableFuture<>();
                yield pendingOperation;
            }
            
            case IDLE, ERROR, DISCONNECTED, CLOSED -> {
                // Para reconexões após fechamento, cria nova instância limpa
                if (current.status() == ConnectionStatus.DISCONNECTED || 
                    current.status() == ConnectionStatus.CLOSED ||
                    current.status() == ConnectionStatus.ERROR) {
                    currentResult = ConnectionResult.idle(current.exchangeName()).connecting();
                } else {
                    currentResult = current.connecting();
                }
                pendingOperation = new CompletableFuture<>();
                yield pendingOperation;
            }
            
            case CLOSING, RECONNECTING -> 
                CompletableFuture.completedFuture(
                    ConnectionResult.failure("Cannot connect while " + current.status().name().toLowerCase())
                );
        };
    }

    public void onConnected() {
        ConnectionResult current = currentResult;
        currentResult = current.connected();
        completePendingOperation();
    }

    public void onClosing(int code, String reason) {
        ConnectionResult current = currentResult;
        currentResult = current.closing(code, reason);
    }

    public void onClosed(int code, String reason) {
        ConnectionResult current = currentResult;
        currentResult = current.closed(code, reason);
        completePendingOperation();
    }

    public void onFailure(String reason, Throwable cause) {
        ConnectionResult current = currentResult;
        currentResult = current.failed(reason, cause);
        completePendingOperation();
    }

    public CompletableFuture<ConnectionResult> startDisconnection() {
        return switch (currentResult.status()) {
            case IDLE, ERROR ->
                CompletableFuture.completedFuture(
                    currentResult.disconnected("Already disconnected - no active connection")
                );

            case DISCONNECTED, CLOSED ->
                CompletableFuture.completedFuture(currentResult);

            case CONNECTING -> {
                if (pendingOperation != null && !pendingOperation.isDone()) {
                    pendingOperation.cancel(true);
                }
                yield CompletableFuture.completedFuture(
                    currentResult.disconnected("Connection attempt cancelled")
                );
            }

            case CONNECTED, CLOSING, RECONNECTING -> {
                pendingOperation = new CompletableFuture<>();
                yield pendingOperation;
            }
        };
    }

    private void completePendingOperation() {
        if (pendingOperation != null && !pendingOperation.isDone()) {
            pendingOperation.complete(currentResult);
        }
    }

    public ConnectionResult getConnectionResult() {
        if (pendingOperation.isDone()) {
            try {
                return pendingOperation.get();
            } catch (Exception e) {
                return currentResult.failed("Future access error", e);
            }
        }
        return currentResult;
    }
}
