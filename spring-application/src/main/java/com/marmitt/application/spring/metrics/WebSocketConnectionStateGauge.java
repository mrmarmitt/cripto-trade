package com.marmitt.application.spring.metrics;

import com.marmitt.core.enums.StreamChannel;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Gauge {@code websocket.connection.state} por (exchange, channel) (T23 G1).
 *
 * <p>Valor {@code 1} = conectado, {@code 0} = desconectado/falha. Combinar gauge=1 com
 * ausencia de mensagens permite detectar conexao "ghost" (OPEN mas silenciosa).
 *
 * <p>Mantem um {@link AtomicInteger} por combinacao de tags; o gauge e registrado uma
 * unica vez na primeira observacao daquela combinacao e passa a refletir o holder.
 */
@Component
public class WebSocketConnectionStateGauge {

    static final String METRIC = "websocket.connection.state";

    private final MeterRegistry meterRegistry;
    private final Map<String, AtomicInteger> states = new ConcurrentHashMap<>();

    public WebSocketConnectionStateGauge(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void markConnected(String exchange, StreamChannel channel) {
        state(exchange, channel).set(1);
    }

    public void markDisconnected(String exchange, StreamChannel channel) {
        state(exchange, channel).set(0);
    }

    private AtomicInteger state(String exchange, StreamChannel channel) {
        String safeExchange = exchange != null ? exchange : "UNKNOWN";
        String safeChannel = channel != null ? channel.name() : "UNKNOWN";
        String key = safeExchange + '|' + safeChannel;
        return states.computeIfAbsent(key, ignored -> {
            AtomicInteger holder = new AtomicInteger(0);
            Gauge.builder(METRIC, holder, AtomicInteger::get)
                    .tag("exchange", safeExchange)
                    .tag("channel", safeChannel)
                    .register(meterRegistry);
            return holder;
        });
    }
}
