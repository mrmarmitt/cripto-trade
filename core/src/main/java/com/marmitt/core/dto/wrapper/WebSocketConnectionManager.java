package com.marmitt.core.dto.wrapper;

import com.marmitt.core.domain.ConnectionResult;
import com.marmitt.core.domain.ConnectionStats;
import com.marmitt.core.enums.ConnectionStatus;
import com.marmitt.core.exceptions.IllegalStateTransitionException;
import lombok.Getter;

import java.util.Set;
import java.util.UUID;

public class WebSocketConnectionManager {

    @Getter
    private final String exchangeName;
    private final ConnectionStats currentConnectionStats;
    private volatile ConnectionResult currentConnectionResult;

    private WebSocketConnectionManager(ConnectionResult connectionResult, String exchangeName) {
        this.exchangeName = exchangeName;
        this.currentConnectionResult = connectionResult;
        this.currentConnectionStats = createStatsForExchange();
    }

    public static WebSocketConnectionManager forExchange(String exchangeName) {
        return new WebSocketConnectionManager(ConnectionResult.idle(), exchangeName);
    }
    
    private static ConnectionStats createStatsForExchange() {
        return ConnectionStats.empty();
    }

    public void setConnectionResult(ConnectionResult newResult) {
        ConnectionStatus currentStatus = currentConnectionResult.status();
        ConnectionStatus newStatus = newResult.status();
        
        // Validação de transições válidas
        if (!isValidTransition(currentStatus, newStatus)) {
            throw new IllegalStateTransitionException(
                String.format("Invalid transition from %s to %s", currentStatus, newStatus)
            );
        }
        
        // Atualiza estatísticas baseado na transição
        updateStatsForTransition(currentStatus, newStatus);
        
        // Atualiza o resultado
        this.currentConnectionResult = newResult;
    }

    public UUID getConnectionId() {
        return getConnectionResult().connectionId();
    }

    public ConnectionStats getConnectionStats() {
        return currentConnectionStats;
    }

    public ConnectionResult getConnectionResult() {
        return currentConnectionResult;
    }

    public void onMessageReceived() {
        currentConnectionStats.recordMessage();
    }

    public void onMessageError(String errorType) {
        currentConnectionStats.recordError();
    }

    public void resetConnection() {
        if (currentConnectionResult.status() == ConnectionStatus.ERROR ||
                currentConnectionResult.status() == ConnectionStatus.CLOSED) {

            this.currentConnectionResult = ConnectionResult.idle();
            currentConnectionStats.resetCounters();
        }
    }

    private boolean isValidTransition(ConnectionStatus from, ConnectionStatus to) {
        return switch (from) {
            case IDLE -> Set.of(ConnectionStatus.CONNECTING, ConnectionStatus.ERROR).contains(to);
            case CONNECTING -> Set.of(ConnectionStatus.CONNECTED, ConnectionStatus.ERROR, ConnectionStatus.DISCONNECTED, ConnectionStatus.CLOSING).contains(to);
            case CONNECTED -> Set.of(ConnectionStatus.CLOSING, ConnectionStatus.DISCONNECTING, ConnectionStatus.ERROR).contains(to);
            case CLOSING -> Set.of(ConnectionStatus.CLOSED, ConnectionStatus.DISCONNECTED, ConnectionStatus.ERROR).contains(to);
            case DISCONNECTING -> Set.of(ConnectionStatus.DISCONNECTED, ConnectionStatus.ERROR).contains(to);
            case DISCONNECTED -> Set.of(ConnectionStatus.CONNECTING, ConnectionStatus.IDLE, ConnectionStatus.ERROR).contains(to);
            case RECONNECTING -> Set.of(ConnectionStatus.CONNECTED, ConnectionStatus.ERROR, ConnectionStatus.DISCONNECTED).contains(to);
            case ERROR, CLOSED -> true;
        };
    }

    private void updateStatsForTransition(ConnectionStatus from, ConnectionStatus to) {
        switch (from) {
            case CONNECTING -> handleFromConnecting(to);
            case CONNECTED -> handleFromConnected(to);
            case DISCONNECTING -> handleFromDisconnecting(to);
            case RECONNECTING -> handleFromReconnecting(to);
        }
        
        if (to == ConnectionStatus.ERROR) {
            currentConnectionStats.recordError();
        }
    }
    
    private void handleFromConnecting(ConnectionStatus to) {
        if (to == ConnectionStatus.CONNECTED) {
            currentConnectionStats.recordConnection();
        }
    }
    
    private void handleFromConnected(ConnectionStatus to) {
        if (to == ConnectionStatus.ERROR) {
            currentConnectionStats.recordDisconnection();
        }

        if (to == ConnectionStatus.DISCONNECTING || to == ConnectionStatus.CLOSING) {
            // Não registra disconnection aqui, só quando realmente desconectar
        }
    }
    
    private void handleFromDisconnecting(ConnectionStatus to) {
        if (to == ConnectionStatus.DISCONNECTED) {
            currentConnectionStats.recordDisconnection();
        }
    }
    
    private void handleFromReconnecting(ConnectionStatus to) {
        if (to == ConnectionStatus.CONNECTED) {
            currentConnectionStats.recordReconnection();
        }
    }
}
