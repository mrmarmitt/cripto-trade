package com.marmitt.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.core.dto.websocket.MessageContext;
import com.marmitt.core.dto.websocket.WebSocketConnectionManager;
import com.marmitt.core.ports.outbound.websocket.MessageProcessorPort;
import com.marmitt.event.RawMessageReceivedEvent;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.ApplicationEventPublisher;

@Slf4j
public class OkHttp3ListenerConverter {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static WebSocketListener convert(MessageProcessorPort listener,
                                            WebSocketConnectionManager webSocketConnectionManager,
                                            ApplicationEventPublisher eventPublisher) {

        return new WebSocketListener() {
            @Override
            public void onOpen(@NotNull WebSocket webSocket, @NotNull Response response) {
                webSocketConnectionManager.onConnected();
            }

            @Override
            public void onMessage(@NotNull WebSocket webSocket, @NotNull String text) {
                // Recupera correlationId no MDC para logs correlacionados
                // MDC.put("correlationId", event.getContext().correlationId());

                // Cria contexto com correlationId único
                MessageContext context = MessageContext.create(webSocketConnectionManager.getExchangeName(), webSocketConnectionManager.getConnectionId());

                // Registra mensagem recebida nas estatísticas
                log.debug("Before onMessageReceived - current stats: received={}, errors={}", 
                         webSocketConnectionManager.getConnectionStats().getTotalMessagesReceived(),
                         webSocketConnectionManager.getConnectionStats().getTotalErrors());
                         
                webSocketConnectionManager.onMessageReceived();
                
                log.debug("After onMessageReceived - current stats: received={}, errors={}", 
                         webSocketConnectionManager.getConnectionStats().getTotalMessagesReceived(),
                         webSocketConnectionManager.getConnectionStats().getTotalErrors());
                         
                log.info("Message received: length={}, exchange={}", text.length(), webSocketConnectionManager.getExchangeName());

                // Publica evento com correlationId para processamento assíncrono
                RawMessageReceivedEvent event = new RawMessageReceivedEvent(this, text, context);
                eventPublisher.publishEvent(event);

                //TODO: remover essa funcao para local mais apropriado.
                // Verifica se a mensagem contém erros (mantendo compatibilidade)
//                checkForMessageErrors(text, webSocketConnectionManager);

                //  MDC.clear(); // Limpa MDC
            }

            @Override
            public void onClosing(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
                webSocketConnectionManager.onClosing(code, reason);
            }

            @Override
            public void onClosed(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
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
