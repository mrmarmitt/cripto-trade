package com.marmitt.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.core.dto.websocket.WebSocketConnectionManager;
import com.marmitt.core.ports.outbound.WebSocketListenerPort;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.jetbrains.annotations.NotNull;

@Slf4j
public class OkHttp3ListenerConverter {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static WebSocketListener convert(WebSocketListenerPort listener, WebSocketConnectionManager webSocketConnectionManager){

        return new WebSocketListener() {
            @Override
            public void onOpen(@NotNull WebSocket webSocket, @NotNull Response response) {
                webSocketConnectionManager.onConnected();
            }

            @Override
            public void onMessage(@NotNull WebSocket webSocket, @NotNull String text) {
                // Registra mensagem recebida nas estatísticas
                webSocketConnectionManager.onMessageReceived();

                // Processa mensagem normalmente
                listener.onMessage(text);

                // Verifica se a mensagem contém erros
                checkForMessageErrors(text, webSocketConnectionManager);
            }

            @Override
            public void onClosing(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
                webSocketConnectionManager.onClosing(code, reason);
            }

            @Override
            public void onClosed(@NotNull WebSocket webSocket, int code, @NotNull String reason){
                webSocketConnectionManager.onClosed(code, reason);
            }

            @Override
            public void onFailure(@NotNull WebSocket webSocket, @NotNull Throwable throwable, Response response) {
                webSocketConnectionManager.onFailure(throwable.getMessage(), throwable);
            }
        };
    }
    
    /**
     * Verifica se a mensagem recebida contém indicadores de erro e registra nas estatísticas.
     * 
     * @param message mensagem JSON recebida
     * @param manager gerenciador de conexão para registrar erros
     */
    private static void checkForMessageErrors(String message, WebSocketConnectionManager manager) {
        try {
            JsonNode jsonNode = objectMapper.readTree(message);
            
            // Verifica padrões comuns de erro em exchanges
            if (jsonNode.has("error")) {
                String errorMsg = jsonNode.get("error").asText();
                manager.onMessageError("MessageError: " + errorMsg);
                log.warn("WebSocket message error detected: {}", errorMsg);
            }
            
            if (jsonNode.has("status") && "error".equalsIgnoreCase(jsonNode.get("status").asText())) {
                String errorMsg = jsonNode.has("message") ? jsonNode.get("message").asText() : "Unknown error";
                manager.onMessageError("StatusError: " + errorMsg);
                log.warn("WebSocket status error detected: {}", errorMsg);
            }
            
            // Binance error pattern
            if (jsonNode.has("code") && jsonNode.get("code").asInt() != 0) {
                String errorMsg = jsonNode.has("msg") ? jsonNode.get("msg").asText() : "Binance error";
                manager.onMessageError("BinanceError: " + errorMsg);
                log.warn("Binance WebSocket error detected: code={}, msg={}", 
                         jsonNode.get("code").asInt(), errorMsg);
            }
            
        } catch (Exception e) {
            // Se não conseguir parsear JSON, não é necessariamente um erro
            log.trace("Could not parse WebSocket message as JSON (might be normal): {}", e.getMessage());
        }
    }
}
