package com.marmitt.core.dto.websocket;

import com.marmitt.core.domain.ConnectionResult;
import com.marmitt.core.domain.ConnectionStats;
import com.marmitt.core.enums.ConnectionStatus;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

public class WebSocketConnectionManager {

    private volatile ConnectionResult currentResult;
    private volatile ConnectionStats currentStats;
    private volatile CompletableFuture<ConnectionResult> pendingOperation;

    private WebSocketConnectionManager(ConnectionResult connectionResult) {
        this.currentResult = connectionResult;
        this.currentStats = ConnectionStats.empty();
        this.pendingOperation = CompletableFuture.completedFuture(connectionResult);
    }


    public static WebSocketConnectionManager forExchange(String exchangeName) {
        return new WebSocketConnectionManager(ConnectionResult.idle(exchangeName));
    }

    public CompletableFuture<ConnectionResult> startConnection() {
        ConnectionResult currentCopyConnectionResult = currentResult;

        return switch (currentCopyConnectionResult.status()) {
            case CONNECTED -> CompletableFuture.completedFuture(
                    currentCopyConnectionResult.withMetadata("message", "Already connected")
            );

            case CONNECTING -> {
                if (pendingOperation != null && !pendingOperation.isDone()) {
                    yield pendingOperation; // Retorna operação em andamento
                }
                // Se não há operação pendente, inicia nova
                currentResult = currentCopyConnectionResult.connecting();
                pendingOperation = new CompletableFuture<>();
                yield pendingOperation;
            }

            case IDLE, ERROR, DISCONNECTED, CLOSED -> {
                // Para reconexões após fechamento, cria nova instância limpa
                if (currentCopyConnectionResult.status() == ConnectionStatus.DISCONNECTED ||
                        currentCopyConnectionResult.status() == ConnectionStatus.CLOSED ||
                        currentCopyConnectionResult.status() == ConnectionStatus.ERROR) {
                    currentResult = ConnectionResult.idle(currentCopyConnectionResult.exchangeName()).connecting();
                } else {
                    currentResult = currentCopyConnectionResult.connecting();
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
        ConnectionResult currentCopyConnectionResult = currentResult;
        ConnectionStats currentCopyConnectionStats = currentStats;

        currentResult = currentCopyConnectionResult.connected();

        // Atualiza estatísticas localmente
        Instant now = Instant.now();
        if (currentCopyConnectionResult.status() == ConnectionStatus.CONNECTING) {
            currentStats = new ConnectionStats(
                    currentCopyConnectionStats.totalConnections() + 1,
                    currentCopyConnectionStats.totalReconnections(),
                    currentCopyConnectionStats.totalMessagesReceived(),
                    currentCopyConnectionStats.totalErrors(),
                    now,
                    currentCopyConnectionStats.lastMessageAt()
            );
        } else if (currentCopyConnectionResult.status() == ConnectionStatus.RECONNECTING) {
            currentStats = new ConnectionStats(
                    currentCopyConnectionStats.totalConnections(),
                    currentCopyConnectionStats.totalReconnections() + 1,
                    currentCopyConnectionStats.totalMessagesReceived(),
                    currentCopyConnectionStats.totalErrors(),
                    now,
                    currentCopyConnectionStats.lastMessageAt()
            );
        }

        completePendingOperation();
    }

    public void onClosing(int code, String reason) {
        ConnectionResult currentCopyConnectionResult = currentResult;
        currentResult = currentCopyConnectionResult.closing(code, reason);
    }

    public void onClosed(int code, String reason) {
        ConnectionResult currentCopyConnectionResult = currentResult;
        currentResult = currentCopyConnectionResult.closed(code, reason);
        completePendingOperation();
    }

    public void onFailure(String reason, Throwable cause) {
        ConnectionResult currentCopyConnectionResult = currentResult;
        ConnectionStats currentCopyConnectionStats = currentStats;

        currentResult = currentCopyConnectionResult.failed(reason, cause);

        // Atualiza estatísticas de erro localmente
        currentStats = new ConnectionStats(
                currentCopyConnectionStats.totalConnections(),
                currentCopyConnectionStats.totalReconnections(),
                currentCopyConnectionStats.totalMessagesReceived(),
                currentCopyConnectionStats.totalErrors() + 1,
                currentCopyConnectionStats.lastConnectedAt(),
                currentCopyConnectionStats.lastMessageAt()
        );

        completePendingOperation();
    }

    public CompletableFuture<ConnectionResult> startDisconnection() {
        return switch (currentResult.status()) {
            case IDLE, ERROR -> {
                currentResult = currentResult.disconnected("Already disconnected - no active connection");
                yield CompletableFuture.completedFuture(currentResult);
            }

            case DISCONNECTED, CLOSED -> CompletableFuture.completedFuture(currentResult);

            case CONNECTING -> {
                if (pendingOperation != null && !pendingOperation.isDone()) {
                    pendingOperation.cancel(true);
                }
                currentResult = currentResult.disconnected("Connection attempt cancelled");
                yield CompletableFuture.completedFuture(currentResult);
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

    /**
     * Registra recebimento de mensagem nas estatísticas.
     * Deve ser chamado sempre que uma mensagem é recebida via WebSocket.
     */
    public void onMessageReceived() {
        ConnectionStats currentCopyConnectionStats = currentStats;

        currentStats = new ConnectionStats(
                currentCopyConnectionStats.totalConnections(),
                currentCopyConnectionStats.totalReconnections(),
                currentCopyConnectionStats.totalMessagesReceived() + 1,
                currentCopyConnectionStats.totalErrors(),
                currentCopyConnectionStats.lastConnectedAt(),
                Instant.now()
        );
    }

    /**
     * Registra erro específico de mensagem (ex: erro JSON dentro de mensagem).
     *
     * @param errorType tipo do erro para categorização (não utilizado nesta implementação)
     */
    public void onMessageError(String errorType) {
        currentStats = new ConnectionStats(
                currentStats.totalConnections(),
                currentStats.totalReconnections(),
                currentStats.totalMessagesReceived(),
                currentStats.totalErrors() + 1,
                currentStats.lastConnectedAt(),
                currentStats.lastMessageAt()
        );
    }

    /**
     * Retorna as estatísticas atuais da conexão.
     */
    public ConnectionStats getConnectionStats() {
        return currentStats;
    }

    /**
     * Reseta as estatísticas da conexão.
     */
    public void resetStats() {
        currentStats = ConnectionStats.empty();
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
