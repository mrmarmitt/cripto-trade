package com.marmitt.core.dto.websocket;

import com.marmitt.core.domain.ConnectionResult;
import com.marmitt.core.domain.ConnectionStats;
import com.marmitt.core.enums.ConnectionStatus;
import lombok.Getter;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class WebSocketConnectionManager {

    @Getter
    private final String exchangeName;
    private volatile ConnectionStats currentConnectionStats;
    private volatile ConnectionResult currentConnectionResult;
    private volatile CompletableFuture<ConnectionResult> pendingOperation;

    private WebSocketConnectionManager(ConnectionResult connectionResult, String exchangeName) {
        this.exchangeName = exchangeName;
        this.currentConnectionResult = connectionResult;
        this.currentConnectionStats = createStatsForExchange();
        this.pendingOperation = CompletableFuture.completedFuture(connectionResult);
    }

    public static WebSocketConnectionManager forExchange(String exchangeName) {
        return new WebSocketConnectionManager(ConnectionResult.idle(), exchangeName);
    }
    
    /**
     * Cria estatísticas iniciais zeradas.
     */
    private static ConnectionStats createStatsForExchange() {
        return ConnectionStats.empty();
    }

    public CompletableFuture<ConnectionResult> startConnection() {
        ConnectionResult currentCopyConnectionResult = currentConnectionResult;

        return switch (currentCopyConnectionResult.status()) {
            case CONNECTED -> CompletableFuture.completedFuture(
                    currentCopyConnectionResult.withMetadata("message", "Already connected")
            );

            case CONNECTING -> {
                if (pendingOperation != null && !pendingOperation.isDone()) {
                    yield pendingOperation; // Retorna operação em andamento
                }
                // Se não há operação pendente, inicia nova
                currentConnectionResult = currentCopyConnectionResult.connecting();
                pendingOperation = new CompletableFuture<>();
                yield pendingOperation;
            }

            case IDLE, ERROR, DISCONNECTED, CLOSED -> {
                // Para reconexões após fechamento, cria nova instância limpa
                if (currentCopyConnectionResult.status() == ConnectionStatus.DISCONNECTED ||
                        currentCopyConnectionResult.status() == ConnectionStatus.CLOSED ||
                        currentCopyConnectionResult.status() == ConnectionStatus.ERROR) {
                    currentConnectionResult = ConnectionResult.idle().connecting();
                } else {
                    currentConnectionResult = currentCopyConnectionResult.connecting();
                }
                pendingOperation = new CompletableFuture<>();
                yield pendingOperation;
            }

            case CLOSING, RECONNECTING -> CompletableFuture.completedFuture(
                    ConnectionResult.failure("Cannot connect while " + currentCopyConnectionResult.status().name().toLowerCase())
            );
        };
    }

    public void onConnected() {
        ConnectionResult currentCopyConnectionResult = currentConnectionResult;
        ConnectionStats currentCopyConnectionStats = currentConnectionStats;

        currentConnectionResult = currentCopyConnectionResult.connected();

        // Atualiza estatísticas localmente
        if (currentCopyConnectionResult.status() == ConnectionStatus.CONNECTING) {
            currentConnectionStats.recordConnection();
        } else if (currentCopyConnectionResult.status() == ConnectionStatus.RECONNECTING) {
            currentConnectionStats.recordReconnection();
        }

        completePendingOperation();
    }

    public void onClosing(int code, String reason) {
        ConnectionResult currentCopyConnectionResult = currentConnectionResult;
        currentConnectionResult = currentCopyConnectionResult.closing(code, reason);
    }

    public void onClosed(int code, String reason) {
        ConnectionResult currentCopyConnectionResult = currentConnectionResult;
        currentConnectionResult = currentCopyConnectionResult.closed(code, reason);
        completePendingOperation();
    }

    public void onFailure(String reason, Throwable cause) {
        ConnectionResult currentCopyConnectionResult = currentConnectionResult;
        ConnectionStats currentCopyConnectionStats = currentConnectionStats;

        currentConnectionResult = currentCopyConnectionResult.failed(reason, cause);

        // Atualiza estatísticas de erro localmente
        currentConnectionStats.recordError();

        completePendingOperation();
    }

    public CompletableFuture<ConnectionResult> startDisconnection() {
        ConnectionResult copyConnectionResult = currentConnectionResult;
        return switch (currentConnectionResult.status()) {
            case IDLE, ERROR -> {
                currentConnectionResult = copyConnectionResult.disconnected("Already disconnected - no active connection");
                yield CompletableFuture.completedFuture(currentConnectionResult);
            }

            case DISCONNECTED, CLOSED -> CompletableFuture.completedFuture(currentConnectionResult);

            case CONNECTING -> {
                if (pendingOperation != null && !pendingOperation.isDone()) {
                    pendingOperation.cancel(true);
                }
                currentConnectionResult = copyConnectionResult.disconnected("Connection attempt cancelled");
                yield CompletableFuture.completedFuture(currentConnectionResult);
            }

            case CONNECTED, CLOSING, RECONNECTING -> {
                pendingOperation = new CompletableFuture<>();
                yield pendingOperation;
            }
        };
    }

    private void completePendingOperation() {
        if (pendingOperation != null && !pendingOperation.isDone()) {
            pendingOperation.complete(currentConnectionResult);
        }
    }

    /**
     * Registra recebimento de mensagem nas estatísticas.
     * Deve ser chamado sempre que uma mensagem é recebida via WebSocket.
     */
    public void onMessageReceived() {
        currentConnectionStats.recordMessage();
    }

    /**
     * Registra erro específico de mensagem (ex: erro JSON dentro de mensagem).
     *
     * @param errorType tipo do erro para categorização (não utilizado nesta implementação)
     */
    public void onMessageError(String errorType) {
        currentConnectionStats.recordError();
    }

    /**
     * Reseta as estatísticas da conexão, preservando o connectionStartTime.
     */
    public void resetStats() {
        currentConnectionStats.resetCounters();
    }

    public UUID getConnectionId() {
        return getConnectionResult().connectionId();
    }

    public ConnectionStats getConnectionStats() {
        return currentConnectionStats;
    }

    public ConnectionResult getConnectionResult() {
        if (pendingOperation.isDone()) {
            try {
                return pendingOperation.get();
            } catch (Exception e) {
                return currentConnectionResult.failed("Future access error", e);
            }
        }
        return currentConnectionResult;
    }
}
