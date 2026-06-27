package com.marmitt.application.spring.metrics;

import com.marmitt.core.enums.StreamChannel;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Gauge {@code websocket.connection.state} por (exchange, channel) (T23 G1).
 *
 * <p>Valor {@code 1} = conectado, {@code 0} = desconectado/falha. Combinar gauge=1 com
 * ausencia de mensagens permite detectar conexao "ghost" (OPEN mas silenciosa).
 *
 * <p><b>Connection-id aware:</b> cada conexao bem-sucedida registra seu {@code connectionId}
 * como o ativo. Um evento de desconexao so zera o gauge se vier da conexao ativa (ou se nao
 * trouxer {@code connectionId}, caso de falha critica). Isso evita que o {@code close} tardio
 * de um socket superado — comum apos reconnect — derrube o gauge enquanto a conexao nova esta
 * viva, espelhando a checagem de "stale close" do {@code ConnectionClosedHandler}.
 *
 * <p>Mantem um {@link State} por combinacao de tags; o gauge e registrado uma unica vez na
 * primeira observacao daquela combinacao e passa a refletir o holder.
 */
@Component
public class WebSocketConnectionStateGauge {

    static final String METRIC = "websocket.connection.state";

    private final MeterRegistry meterRegistry;
    private final Map<String, State> states = new ConcurrentHashMap<>();

    public WebSocketConnectionStateGauge(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void markConnected(String exchange, StreamChannel channel, UUID connectionId) {
        State state = state(exchange, channel);
        state.activeConnectionId = connectionId;
        state.value.set(1);
    }

    public void markDisconnected(String exchange, StreamChannel channel, UUID connectionId) {
        State state = state(exchange, channel);
        UUID active = state.activeConnectionId;
        // Ignora close/fail tardio de uma conexao ja superada por outra ativa.
        if (connectionId != null && active != null && !connectionId.equals(active)) {
            return;
        }
        state.activeConnectionId = null;
        state.value.set(0);
    }

    private State state(String exchange, StreamChannel channel) {
        String safeExchange = exchange != null ? exchange : "UNKNOWN";
        String safeChannel = channel != null ? channel.name() : "UNKNOWN";
        String key = safeExchange + '|' + safeChannel;
        return states.computeIfAbsent(key, ignored -> {
            State state = new State();
            Gauge.builder(METRIC, state.value, AtomicInteger::get)
                    .tag("exchange", safeExchange)
                    .tag("channel", safeChannel)
                    .register(meterRegistry);
            return state;
        });
    }

    private static final class State {
        private final AtomicInteger value = new AtomicInteger(0);
        private volatile UUID activeConnectionId;
    }
}
